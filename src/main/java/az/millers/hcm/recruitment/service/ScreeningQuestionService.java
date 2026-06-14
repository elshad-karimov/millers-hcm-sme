package az.millers.hcm.recruitment.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.recruitment.api.dto.ScreeningDtos.AnswerResponse;
import az.millers.hcm.recruitment.api.dto.ScreeningDtos.PublicQuestion;
import az.millers.hcm.recruitment.api.dto.ScreeningDtos.QuestionRequest;
import az.millers.hcm.recruitment.api.dto.ScreeningDtos.QuestionResponse;
import az.millers.hcm.recruitment.api.dto.ScreeningDtos.ScreeningAnswer;
import az.millers.hcm.recruitment.api.dto.ScreeningDtos.ScreeningResult;
import az.millers.hcm.recruitment.domain.ApplicationAnswer;
import az.millers.hcm.recruitment.domain.ScreeningQuestion;
import az.millers.hcm.recruitment.domain.ScreeningQuestionType;
import az.millers.hcm.recruitment.repo.ApplicationAnswerRepository;
import az.millers.hcm.recruitment.repo.JobPostingRepository;
import az.millers.hcm.recruitment.repo.ScreeningQuestionRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * M289 — Recruitment PRD §15: knockout / pre-screening questions.
 *
 * <p>HR manages an ordered set of questions per {@link
 * az.millers.hcm.recruitment.domain.JobPosting}; candidates answer them
 * at apply time; {@link #evaluate} stores the answers, computes pass/fail
 * per the question rule, and reports whether any <b>knockout</b> question
 * failed. The public-portal apply flow uses that verdict to auto-reject.
 */
@Service
public class ScreeningQuestionService {

    private static final Logger log = LoggerFactory.getLogger(ScreeningQuestionService.class);
    private static final String MODULE = "RECRUITMENT";
    private static final String ENTITY = "ScreeningQuestion";

    private final ScreeningQuestionRepository questions;
    private final ApplicationAnswerRepository answers;
    private final JobPostingRepository postings;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public ScreeningQuestionService(ScreeningQuestionRepository questions,
                                     ApplicationAnswerRepository answers,
                                     JobPostingRepository postings,
                                     AuditService audit,
                                     CurrentRequest currentRequest) {
        this.questions = questions;
        this.answers = answers;
        this.postings = postings;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    // ── HR-side CRUD ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<QuestionResponse> listForPosting(UUID postingId) {
        return questions.findByPostingIdOrderByOrdinalAscCreatedAtAsc(postingId)
                .stream().map(QuestionResponse::from).toList();
    }

    @Transactional
    public QuestionResponse create(UUID postingId, QuestionRequest req) {
        if (!postings.existsById(postingId)) {
            throw new ResourceNotFoundException("Posting not found: " + postingId);
        }
        ScreeningQuestion q = new ScreeningQuestion();
        q.setPostingId(postingId);
        apply(q, req);
        q.setCreatedBy(currentRequest.username());
        q.setUpdatedBy(currentRequest.username());
        ScreeningQuestion saved = questions.save(q);
        audit.record(MODULE, ENTITY, saved.getId().toString(), "CREATE", null,
                Map.of("postingId", postingId.toString(),
                        "type", saved.getQuestionType().name(),
                        "knockout", saved.isKnockout()));
        return QuestionResponse.from(saved);
    }

    @Transactional
    public QuestionResponse update(UUID id, QuestionRequest req) {
        ScreeningQuestion q = questions.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found: " + id));
        apply(q, req);
        q.setUpdatedBy(currentRequest.username());
        ScreeningQuestion saved = questions.save(q);
        audit.record(MODULE, ENTITY, id.toString(), "UPDATE", null,
                Map.of("type", saved.getQuestionType().name(),
                        "knockout", saved.isKnockout()));
        return QuestionResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        ScreeningQuestion q = questions.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found: " + id));
        questions.delete(q);
        audit.record(MODULE, ENTITY, id.toString(), "DELETE", null, null);
    }

    /** Map the request onto the entity + validate the rule is coherent. */
    private void apply(ScreeningQuestion q, QuestionRequest req) {
        q.setOrdinal(req.ordinal());
        q.setQuestionText(req.questionText());
        q.setQuestionType(req.questionType());
        q.setOptions(req.options());
        q.setKnockout(req.knockout());
        q.setRequired(req.required());
        q.setExpectedBoolean(req.expectedBoolean());
        q.setAcceptableOptions(req.acceptableOptions());
        q.setMinValue(req.minValue());
        q.setMaxValue(req.maxValue());
        if (req.active() != null) q.setActive(req.active());

        if (req.questionType() == ScreeningQuestionType.NUMERIC
                && req.minValue() != null && req.maxValue() != null
                && req.minValue().compareTo(req.maxValue()) > 0) {
            throw new BadRequestException("Minimum value cannot exceed maximum value");
        }
        if (req.questionType() == ScreeningQuestionType.SINGLE_CHOICE
                && (req.options() == null || req.options().isBlank())) {
            throw new BadRequestException("A single-choice question needs at least one option");
        }
    }

    // ── Candidate-facing ───────────────────────────────────────────────

    /** Active questions for a posting, without the pass/fail rule. */
    @Transactional(readOnly = true)
    public List<PublicQuestion> publicQuestionsForPosting(UUID postingId) {
        return questions.findByPostingIdAndActiveTrueOrderByOrdinalAscCreatedAtAsc(postingId)
                .stream().map(PublicQuestion::from).toList();
    }

    /**
     * Store the candidate's answers for an application and compute the
     * verdict. Every active question is recorded (unanswered ones too,
     * so the recruiter sees gaps); a knockout question that fails its
     * rule lands in {@code failedKnockouts}.
     */
    @Transactional
    public ScreeningResult evaluate(UUID applicationId, UUID postingId,
                                     List<ScreeningAnswer> submitted) {
        List<ScreeningQuestion> active =
                questions.findByPostingIdAndActiveTrueOrderByOrdinalAscCreatedAtAsc(postingId);
        if (active.isEmpty()) {
            return new ScreeningResult(false, 0, List.of());
        }
        Map<UUID, String> byId = new java.util.HashMap<>();
        if (submitted != null) {
            for (ScreeningAnswer a : submitted) {
                if (a != null && a.questionId() != null) byId.put(a.questionId(), a.answerText());
            }
        }

        List<String> failedKnockouts = new ArrayList<>();
        int answered = 0;
        for (ScreeningQuestion q : active) {
            String answer = byId.get(q.getId());
            boolean hasAnswer = answer != null && !answer.isBlank();
            if (hasAnswer) answered++;
            boolean passed = passes(q, answer);

            ApplicationAnswer row = new ApplicationAnswer();
            row.setApplicationId(applicationId);
            row.setQuestionId(q.getId());
            row.setQuestionTextSnapshot(q.getQuestionText());
            row.setAnswerText(answer);
            row.setPassed(passed);
            row.setKnockout(q.isKnockout());
            answers.save(row);

            if (q.isKnockout() && !passed) {
                failedKnockouts.add(q.getQuestionText());
            }
        }
        boolean knockedOut = !failedKnockouts.isEmpty();
        if (knockedOut) {
            log.info("Screening knockout on application {} ({} failed question(s))",
                    applicationId, failedKnockouts.size());
        }
        return new ScreeningResult(knockedOut, answered, failedKnockouts);
    }

    /**
     * Does this answer satisfy the question's rule? A required question
     * with no answer fails; an optional unanswered question passes.
     */
    boolean passes(ScreeningQuestion q, String answer) {
        boolean hasAnswer = answer != null && !answer.isBlank();
        if (!hasAnswer) {
            return !q.isRequired();
        }
        String a = answer.trim();
        return switch (q.getQuestionType()) {
            case BOOLEAN -> {
                Boolean parsed = parseBool(a);
                if (parsed == null) yield false;            // unparseable yes/no
                yield q.getExpectedBoolean() == null || parsed.equals(q.getExpectedBoolean());
            }
            case SINGLE_CHOICE -> {
                if (q.getAcceptableOptions() == null || q.getAcceptableOptions().isBlank()) {
                    yield true;                              // no rule → informational only
                }
                yield splitLines(q.getAcceptableOptions()).stream()
                        .anyMatch(opt -> opt.equalsIgnoreCase(a));
            }
            case NUMERIC -> {
                BigDecimal v;
                try {
                    v = new BigDecimal(a);
                } catch (NumberFormatException ex) {
                    yield false;
                }
                if (q.getMinValue() != null && v.compareTo(q.getMinValue()) < 0) yield false;
                if (q.getMaxValue() != null && v.compareTo(q.getMaxValue()) > 0) yield false;
                yield true;
            }
        };
    }

    private static Boolean parseBool(String s) {
        String v = s.trim().toLowerCase();
        return switch (v) {
            case "true", "yes", "y", "1" -> Boolean.TRUE;
            case "false", "no", "n", "0" -> Boolean.FALSE;
            default -> null;
        };
    }

    private static List<String> splitLines(String s) {
        List<String> out = new ArrayList<>();
        for (String line : s.split("\\r?\\n")) {
            String t = line.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    // ── Recruiter view ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AnswerResponse> answersForApplication(UUID applicationId) {
        return answers.findByApplicationIdOrderByCreatedAtAsc(applicationId)
                .stream().map(AnswerResponse::from).toList();
    }
}
