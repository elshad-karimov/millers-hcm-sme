package az.millers.hcm.recruitment.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.recruitment.api.dto.InterviewDtos.InterviewDetail;
import az.millers.hcm.recruitment.api.dto.InterviewDtos.InterviewFinalize;
import az.millers.hcm.recruitment.api.dto.InterviewDtos.InterviewResponse;
import az.millers.hcm.recruitment.api.dto.InterviewDtos.InterviewSchedule;
import az.millers.hcm.recruitment.api.dto.InterviewDtos.KitResponse;
import az.millers.hcm.recruitment.api.dto.InterviewDtos.QuestionResponse;
import az.millers.hcm.recruitment.api.dto.InterviewDtos.ScoreResponse;
import az.millers.hcm.recruitment.api.dto.InterviewDtos.ScoreUpsert;
import az.millers.hcm.recruitment.domain.Interview;
import az.millers.hcm.recruitment.domain.InterviewKit;
import az.millers.hcm.recruitment.domain.InterviewQuestion;
import az.millers.hcm.recruitment.domain.InterviewScore;
import az.millers.hcm.recruitment.domain.InterviewStatus;
import az.millers.hcm.recruitment.repo.ApplicationRepository;
import az.millers.hcm.recruitment.repo.InterviewKitRepository;
import az.millers.hcm.recruitment.repo.InterviewQuestionRepository;
import az.millers.hcm.recruitment.repo.InterviewRepository;
import az.millers.hcm.recruitment.repo.InterviewScoreRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * Interview lifecycle service (M85):
 * <ul>
 *   <li>{@link #schedule}    — SCHEDULED row with auto-numbered interview_no</li>
 *   <li>{@link #upsertScore} — write/update one score (interview moves to IN_PROGRESS)</li>
 *   <li>{@link #finalize_}   — compute overall_score (weighted avg), set COMPLETED</li>
 *   <li>{@link #cancel} / {@link #markNoShow} — terminal sinks</li>
 * </ul>
 *
 * <p>Per-question scores are unique on {@code (interview, question)}; the
 * service upserts so re-scoring overwrites cleanly.
 */
@Service
public class InterviewService {

    private static final String MODULE = "RECRUITMENT";
    private static final String ENTITY = "Interview";

    private final InterviewRepository interviews;
    private final InterviewKitRepository kits;
    private final InterviewQuestionRepository questions;
    private final InterviewScoreRepository scores;
    private final ApplicationRepository applications;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public InterviewService(InterviewRepository interviews,
                             InterviewKitRepository kits,
                             InterviewQuestionRepository questions,
                             InterviewScoreRepository scores,
                             ApplicationRepository applications,
                             AuditService audit,
                             CurrentRequest currentRequest) {
        this.interviews = interviews;
        this.kits = kits;
        this.questions = questions;
        this.scores = scores;
        this.applications = applications;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Interview> listForApplication(UUID applicationId) {
        return interviews.findByApplicationIdOrderByScheduledAtDesc(applicationId);
    }

    @Transactional(readOnly = true)
    public List<Interview> listForInterviewer(UUID employeeId, InterviewStatus status) {
        return status == null
                ? interviews.findByInterviewerEmployeeIdOrderByScheduledAtDesc(employeeId)
                : interviews.findByInterviewerAndStatus(employeeId, status);
    }

    @Transactional(readOnly = true)
    public Interview get(UUID id) {
        return interviews.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Interview not found: " + id));
    }

    /** Composite view — interview + kit + questions + scores in one round-trip. */
    @Transactional(readOnly = true)
    public InterviewDetail detail(UUID id) {
        Interview iv = get(id);
        InterviewKit kit = kits.findById(iv.getKitId())
                .orElseThrow(() -> new IllegalStateException(
                        "Kit vanished: " + iv.getKitId()));
        List<QuestionResponse> qs = questions
                .findByKitIdOrderBySortOrderAscIdAsc(iv.getKitId())
                .stream().map(QuestionResponse::from).toList();
        List<ScoreResponse> ss = scores.findByInterviewId(id)
                .stream().map(ScoreResponse::from).toList();
        return new InterviewDetail(
                InterviewResponse.from(iv),
                KitResponse.from(kit),
                qs, ss);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Transactional
    public Interview schedule(InterviewSchedule req) {
        if (!applications.existsById(req.applicationId())) {
            throw new BadRequestException("Application not found: " + req.applicationId());
        }
        if (!kits.existsById(req.kitId())) {
            throw new BadRequestException("Kit not found: " + req.kitId());
        }
        Interview iv = new Interview();
        iv.setInterviewNo(String.format("INT-%06d", interviews.nextInterviewNoSequence()));
        iv.setApplicationId(req.applicationId());
        iv.setKitId(req.kitId());
        iv.setInterviewerEmployeeId(req.interviewerEmployeeId());
        iv.setScheduledAt(req.scheduledAt());
        iv.setStatus(InterviewStatus.SCHEDULED);
        iv.setCreatedBy(currentRequest.username());
        iv.setUpdatedBy(currentRequest.username());
        Interview saved = interviews.save(iv);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, InterviewResponse.from(saved));
        return saved;
    }

    @Transactional
    public InterviewScore upsertScore(UUID interviewId, ScoreUpsert req) {
        Interview iv = get(interviewId);
        if (iv.getStatus() == InterviewStatus.COMPLETED
                || iv.getStatus() == InterviewStatus.CANCELLED
                || iv.getStatus() == InterviewStatus.NO_SHOW) {
            throw new BadRequestException(
                    "Cannot score a " + iv.getStatus() + " interview");
        }
        // Validate the question belongs to this interview's kit (defence vs.
        // a UI bug sending a cross-kit question id).
        InterviewQuestion q = questions.findById(req.questionId())
                .orElseThrow(() -> new BadRequestException(
                        "Question not found: " + req.questionId()));
        if (!q.getKitId().equals(iv.getKitId())) {
            throw new BadRequestException(
                    "Question does not belong to this interview's kit");
        }
        // Upsert.
        InterviewScore s = scores.findByInterviewIdAndQuestionId(interviewId, req.questionId())
                .orElseGet(() -> {
                    InterviewScore fresh = new InterviewScore();
                    fresh.setInterviewId(interviewId);
                    fresh.setQuestionId(req.questionId());
                    return fresh;
                });
        ScoreResponse before = s.getId() == null ? null : ScoreResponse.from(s);
        s.setScore(req.score());
        s.setComment(req.comment());
        InterviewScore saved = scores.save(s);

        // First score flips status to IN_PROGRESS.
        if (iv.getStatus() == InterviewStatus.SCHEDULED) {
            iv.setStatus(InterviewStatus.IN_PROGRESS);
            iv.setUpdatedBy(currentRequest.username());
            interviews.save(iv);
        }

        audit.record(MODULE, "InterviewScore", saved.getId().toString(),
                before == null ? "CREATE" : "UPDATE", before, ScoreResponse.from(saved));
        return saved;
    }

    @Transactional
    public Interview finalize_(UUID interviewId, InterviewFinalize req) {
        Interview iv = get(interviewId);
        if (iv.getStatus() == InterviewStatus.COMPLETED) {
            throw new BadRequestException("Interview is already completed");
        }
        if (iv.getStatus() == InterviewStatus.CANCELLED
                || iv.getStatus() == InterviewStatus.NO_SHOW) {
            throw new BadRequestException(
                    "Cannot finalise a " + iv.getStatus() + " interview");
        }
        List<InterviewQuestion> kitQs = questions
                .findByKitIdAndActiveTrueOrderBySortOrderAscIdAsc(iv.getKitId());
        List<InterviewScore> givenScores = scores.findByInterviewId(interviewId);

        // Required-question check.
        Map<UUID, InterviewScore> scoreByQ = new HashMap<>();
        for (InterviewScore s : givenScores) scoreByQ.put(s.getQuestionId(), s);
        for (InterviewQuestion q : kitQs) {
            if (q.isRequired() && !scoreByQ.containsKey(q.getId())) {
                throw new BadRequestException(
                        "Required question is unscored: " + q.getQuestionText());
            }
        }

        // Weighted average over scored questions.
        long weightedSum = 0;
        long weightTotal = 0;
        for (InterviewQuestion q : kitQs) {
            InterviewScore s = scoreByQ.get(q.getId());
            if (s == null) continue;
            weightedSum += (long) s.getScore() * q.getWeight();
            weightTotal += q.getWeight();
        }
        BigDecimal overall = weightTotal == 0
                ? null
                : BigDecimal.valueOf(weightedSum)
                        .divide(BigDecimal.valueOf(weightTotal), 2, RoundingMode.HALF_UP);

        InterviewResponse before = InterviewResponse.from(iv);
        iv.setStatus(InterviewStatus.COMPLETED);
        iv.setOverallScore(overall);
        iv.setRecommendation(req.recommendation());
        iv.setOverallComment(req.overallComment());
        iv.setCompletedAt(OffsetDateTime.now());
        iv.setUpdatedBy(currentRequest.username());
        Interview saved = interviews.save(iv);
        audit.record(MODULE, ENTITY, interviewId.toString(),
                "FINALIZE", before, InterviewResponse.from(saved));
        return saved;
    }

    @Transactional
    public Interview cancel(UUID interviewId, String reason) {
        return transition(interviewId, InterviewStatus.CANCELLED, reason, "CANCEL");
    }

    @Transactional
    public Interview markNoShow(UUID interviewId, String reason) {
        return transition(interviewId, InterviewStatus.NO_SHOW, reason, "NO_SHOW");
    }

    private Interview transition(UUID interviewId, InterviewStatus newStatus,
                                  String reason, String action) {
        Interview iv = get(interviewId);
        if (iv.getStatus() == InterviewStatus.COMPLETED) {
            throw new BadRequestException("Interview is already completed");
        }
        if (iv.getStatus() == newStatus) return iv;
        InterviewResponse before = InterviewResponse.from(iv);
        iv.setStatus(newStatus);
        if (reason != null && !reason.isBlank()) {
            iv.setOverallComment(reason);
        }
        iv.setUpdatedBy(currentRequest.username());
        Interview saved = interviews.save(iv);
        audit.record(MODULE, ENTITY, interviewId.toString(),
                action, before, InterviewResponse.from(saved));
        return saved;
    }
}
