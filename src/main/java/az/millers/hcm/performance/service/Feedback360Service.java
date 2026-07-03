package az.millers.hcm.performance.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.performance.api.dto.Feedback360Dtos.NominateRequest;
import az.millers.hcm.performance.api.dto.Feedback360Dtos.NominationResponse;
import az.millers.hcm.performance.api.dto.Feedback360Dtos.NominationSummary;
import az.millers.hcm.performance.api.dto.Feedback360Dtos.QuestionnaireRequest;
import az.millers.hcm.performance.api.dto.Feedback360Dtos.QuestionnaireResponse;
import az.millers.hcm.performance.api.dto.FeedbackRequest;
import az.millers.hcm.performance.domain.Feedback;
import az.millers.hcm.performance.domain.FeedbackNomination;
import az.millers.hcm.performance.domain.FeedbackQuestion;
import az.millers.hcm.performance.domain.FeedbackQuestionnaire;
import az.millers.hcm.performance.domain.FeedbackRelationship;
import az.millers.hcm.performance.domain.FeedbackVisibility;
import az.millers.hcm.performance.domain.ReviewCycle;
import az.millers.hcm.performance.repo.FeedbackNominationRepository;
import az.millers.hcm.performance.repo.FeedbackQuestionRepository;
import az.millers.hcm.performance.repo.FeedbackQuestionnaireRepository;
import az.millers.hcm.performance.repo.ReviewCycleRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.scope.AccessScopeService;
import jakarta.persistence.EntityManager;

/**
 * HCM_12 M395 — 360° nomination lifecycle (§13.2) + questionnaires (§13.3) +
 * per-cycle min/max reviewer rules (§13.4). Responses are ordinary
 * {@link Feedback} rows submitted through the EXISTING {@link FeedbackService};
 * completion links the nomination to the created feedback.
 */
@Service
public class Feedback360Service {

    private static final String TENANT = "default";
    private static final String MODULE = "PERFORMANCE";
    private static final Set<String> QUESTION_TYPES = Set.of("RATING", "TEXT");
    private static final Set<String> CATEGORIES = Set.of(
            "COMPETENCY", "BEHAVIORAL", "COLLABORATION", "LEADERSHIP",
            "STRENGTHS", "IMPROVEMENT", "OPEN");

    private final FeedbackNominationRepository nominations;
    private final FeedbackQuestionnaireRepository questionnaires;
    private final FeedbackQuestionRepository questions;
    private final ReviewCycleRepository cycles;
    private final EmployeeRepository employees;
    private final FeedbackService feedbackService;
    private final AccessScopeService accessScope;
    private final AuditService audit;
    private final CurrentRequest currentRequest;
    private final EntityManager entityManager;

    public Feedback360Service(FeedbackNominationRepository nominations,
                              FeedbackQuestionnaireRepository questionnaires,
                              FeedbackQuestionRepository questions,
                              ReviewCycleRepository cycles,
                              EmployeeRepository employees,
                              FeedbackService feedbackService,
                              AccessScopeService accessScope,
                              AuditService audit,
                              CurrentRequest currentRequest,
                              EntityManager entityManager) {
        this.nominations = nominations;
        this.questionnaires = questionnaires;
        this.questions = questions;
        this.cycles = cycles;
        this.employees = employees;
        this.feedbackService = feedbackService;
        this.accessScope = accessScope;
        this.audit = audit;
        this.currentRequest = currentRequest;
        this.entityManager = entityManager;
    }

    // ── Questionnaires (§13.3) ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<QuestionnaireResponse> listQuestionnaires(boolean activeOnly) {
        List<FeedbackQuestionnaire> rows = activeOnly
                ? questionnaires.findByTenantIdAndActiveTrueOrderByNameAsc(TENANT)
                : questionnaires.findByTenantIdOrderByNameAsc(TENANT);
        return rows.stream()
                .map(q -> QuestionnaireResponse.from(q,
                        questions.findByQuestionnaireIdOrderByQuestionOrderAsc(q.getId())))
                .toList();
    }

    @Transactional
    public QuestionnaireResponse createQuestionnaire(QuestionnaireRequest req) {
        String code = req.code().trim().toUpperCase();
        if (questionnaires.existsByTenantIdAndCode(TENANT, code)) {
            throw new BadRequestException("Questionnaire code already exists: " + code);
        }
        FeedbackQuestionnaire q = new FeedbackQuestionnaire();
        q.setTenantId(TENANT);
        q.setCode(code);
        q.setCreatedBy(currentRequest.username());
        applyQuestionnaire(q, req);
        FeedbackQuestionnaire saved = questionnaires.save(q);
        replaceQuestions(saved, req);
        audit.record(MODULE, "FeedbackQuestionnaire", saved.getId().toString(), "CREATE",
                null, code + " (" + req.questions().size() + " questions)");
        return QuestionnaireResponse.from(saved,
                questions.findByQuestionnaireIdOrderByQuestionOrderAsc(saved.getId()));
    }

    @Transactional
    public QuestionnaireResponse updateQuestionnaire(UUID id, QuestionnaireRequest req) {
        FeedbackQuestionnaire q = questionnaires.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Questionnaire not found: " + id));
        String code = req.code().trim().toUpperCase();
        if (!q.getCode().equals(code) && questionnaires.existsByTenantIdAndCode(TENANT, code)) {
            throw new BadRequestException("Questionnaire code already exists: " + code);
        }
        q.setCode(code);
        applyQuestionnaire(q, req);
        FeedbackQuestionnaire saved = questionnaires.save(q);
        replaceQuestions(saved, req);
        audit.record(MODULE, "FeedbackQuestionnaire", id.toString(), "UPDATE",
                null, code + " (" + req.questions().size() + " questions)");
        return QuestionnaireResponse.from(saved,
                questions.findByQuestionnaireIdOrderByQuestionOrderAsc(id));
    }

    private void applyQuestionnaire(FeedbackQuestionnaire q, QuestionnaireRequest req) {
        q.setName(req.name());
        q.setDescription(req.description());
        q.setActive(req.active() == null ? true : req.active());
    }

    /** Full-replace pattern (delete + flush + reinsert — the M300 lesson). */
    private void replaceQuestions(FeedbackQuestionnaire q, QuestionnaireRequest req) {
        questions.deleteByQuestionnaireId(q.getId());
        entityManager.flush();
        int order = 1;
        for (var qr : req.questions()) {
            if (qr.questionType() != null && !QUESTION_TYPES.contains(qr.questionType())) {
                throw new BadRequestException("Unknown question type: " + qr.questionType());
            }
            if (qr.category() != null && !CATEGORIES.contains(qr.category())) {
                throw new BadRequestException("Unknown question category: " + qr.category());
            }
            FeedbackQuestion fq = new FeedbackQuestion();
            fq.setTenantId(q.getTenantId());
            fq.setQuestionnaireId(q.getId());
            fq.setQuestionOrder(order++);
            fq.setQuestionText(qr.questionText());
            fq.setQuestionType(qr.questionType() == null ? "RATING" : qr.questionType());
            fq.setCategory(qr.category());
            fq.setRequired(qr.required() == null ? true : qr.required());
            questions.save(fq);
        }
    }

    // ── Nominations (§13.2) ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<NominationResponse> listNominations(UUID cycleId, UUID subjectEmployeeId,
                                                    UUID reviewerEmployeeId) {
        List<FeedbackNomination> rows;
        if (reviewerEmployeeId != null) {
            rows = nominations.findByTenantIdAndReviewerEmployeeIdOrderByCreatedAtDesc(
                    TENANT, reviewerEmployeeId);
        } else if (subjectEmployeeId != null) {
            requireAccessible(subjectEmployeeId);
            rows = nominations.findByTenantIdAndCycleIdAndSubjectEmployeeIdOrderByCreatedAtAsc(
                    TENANT, cycleId, subjectEmployeeId);
        } else {
            rows = nominations.findByTenantIdAndCycleIdOrderByCreatedAtAsc(TENANT, cycleId);
        }
        // GLOBAL RULE 7/8 — filter out subjects outside the caller's scope.
        rows = rows.stream().filter(n -> accessScope.isAccessible(n.getSubjectEmployeeId())
                || accessScope.isAccessible(n.getReviewerEmployeeId())).toList();
        return decorate(rows);
    }

    @Transactional
    public NominationResponse nominate(NominateRequest req) {
        requireAccessible(req.subjectEmployeeId());
        ReviewCycle cycle = cycles.findById(req.cycleId())
                .orElseThrow(() -> new BadRequestException("Cycle not found: " + req.cycleId()));
        if (!employees.existsById(req.reviewerEmployeeId())) {
            throw new BadRequestException("Reviewer not found: " + req.reviewerEmployeeId());
        }
        boolean self = req.subjectEmployeeId().equals(req.reviewerEmployeeId());
        if (self && !"SELF".equals(req.relationship())) {
            throw new BadRequestException("Nominating yourself requires the SELF relationship");
        }
        try {
            FeedbackRelationship.valueOf(req.relationship());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unknown relationship: " + req.relationship());
        }
        if (nominations.existsByCycleIdAndSubjectEmployeeIdAndReviewerEmployeeId(
                req.cycleId(), req.subjectEmployeeId(), req.reviewerEmployeeId())) {
            throw new BadRequestException("This reviewer is already nominated for the subject in this cycle");
        }
        if (req.questionnaireId() != null && !questionnaires.existsById(req.questionnaireId())) {
            throw new BadRequestException("Questionnaire not found: " + req.questionnaireId());
        }
        // §13.4 — maximum reviewers per subject (live nominations only).
        if (cycle.getFeedbackMaxReviewers() != null) {
            long live = nominations.findByTenantIdAndCycleIdAndSubjectEmployeeIdOrderByCreatedAtAsc(
                            TENANT, req.cycleId(), req.subjectEmployeeId()).stream()
                    .filter(n -> !"DECLINED".equals(n.getStatus()) && !"CANCELLED".equals(n.getStatus()))
                    .count();
            if (live >= cycle.getFeedbackMaxReviewers()) {
                throw new BadRequestException("Maximum reviewers reached for this subject ("
                        + cycle.getFeedbackMaxReviewers() + ")");
            }
        }

        FeedbackNomination n = new FeedbackNomination();
        n.setTenantId(TENANT);
        n.setCycleId(req.cycleId());
        n.setSubjectEmployeeId(req.subjectEmployeeId());
        n.setReviewerEmployeeId(req.reviewerEmployeeId());
        n.setRelationship(req.relationship());
        n.setQuestionnaireId(req.questionnaireId());
        n.setAnonymous(req.anonymous() == null ? true : req.anonymous());
        n.setDueDate(req.dueDate());
        n.setNominatedBy(currentRequest.username());
        FeedbackNomination saved = nominations.save(n);
        audit.record(MODULE, "FeedbackNomination", saved.getId().toString(), "NOMINATE",
                null, req.relationship() + " reviewer for " + req.subjectEmployeeId());
        return decorate(List.of(saved)).get(0);
    }

    @Transactional
    public NominationResponse approve(UUID id) {
        FeedbackNomination n = requireNomination(id);
        assertStatus(n, "NOMINATED");
        n.setStatus("APPROVED");
        audit.record(MODULE, "FeedbackNomination", id.toString(), "APPROVE", "NOMINATED", "APPROVED");
        return decorate(List.of(nominations.save(n))).get(0);
    }

    @Transactional
    public NominationResponse decline(UUID id, String reason) {
        FeedbackNomination n = requireNomination(id);
        if (!"NOMINATED".equals(n.getStatus()) && !"APPROVED".equals(n.getStatus())) {
            throw new BadRequestException("Only NOMINATED/APPROVED requests can be declined");
        }
        n.setStatus("DECLINED");
        n.setDeclineReason(reason);
        audit.record(MODULE, "FeedbackNomination", id.toString(), "DECLINE", null, reason);
        return decorate(List.of(nominations.save(n))).get(0);
    }

    @Transactional
    public NominationResponse cancel(UUID id) {
        FeedbackNomination n = requireNomination(id);
        if ("COMPLETED".equals(n.getStatus())) {
            throw new BadRequestException("A completed request cannot be cancelled");
        }
        n.setStatus("CANCELLED");
        audit.record(MODULE, "FeedbackNomination", id.toString(), "CANCEL", null, "CANCELLED");
        return decorate(List.of(nominations.save(n))).get(0);
    }

    /**
     * Reviewer submits the feedback: the response is a normal feedback row created
     * through the existing FeedbackService (cycle/subject/relationship/anonymity
     * are FORCED from the nomination — the caller only supplies content).
     */
    @Transactional
    public NominationResponse complete(UUID id, FeedbackRequest content) {
        FeedbackNomination n = requireNomination(id);
        if (!"NOMINATED".equals(n.getStatus()) && !"APPROVED".equals(n.getStatus())) {
            throw new BadRequestException("Only NOMINATED/APPROVED requests can be completed (was "
                    + n.getStatus() + ")");
        }
        Feedback f = feedbackService.submit(new FeedbackRequest(
                n.getCycleId(),
                n.getSubjectEmployeeId(),
                n.getReviewerEmployeeId(),
                FeedbackRelationship.valueOf(n.getRelationship()),
                n.isAnonymous() ? FeedbackVisibility.ANONYMOUS : FeedbackVisibility.NORMAL,
                content.overallRating(),
                content.strengths(),
                content.improvements(),
                content.comments(),
                content.competencies(),
                true));
        n.setStatus("COMPLETED");
        n.setFeedbackId(f.getId());
        audit.record(MODULE, "FeedbackNomination", id.toString(), "COMPLETE",
                null, Map.of("feedbackId", f.getId().toString()));
        return decorate(List.of(nominations.save(n))).get(0);
    }

    /** §13.4 completeness for a subject in a cycle. */
    @Transactional(readOnly = true)
    public NominationSummary summary(UUID cycleId, UUID subjectEmployeeId) {
        requireAccessible(subjectEmployeeId);
        ReviewCycle cycle = cycles.findById(cycleId)
                .orElseThrow(() -> new BadRequestException("Cycle not found: " + cycleId));
        List<FeedbackNomination> rows = nominations
                .findByTenantIdAndCycleIdAndSubjectEmployeeIdOrderByCreatedAtAsc(
                        TENANT, cycleId, subjectEmployeeId);
        int nominated = 0, approved = 0, completed = 0, declined = 0;
        for (FeedbackNomination n : rows) {
            switch (n.getStatus()) {
                case "NOMINATED" -> nominated++;
                case "APPROVED" -> approved++;
                case "COMPLETED" -> completed++;
                case "DECLINED" -> declined++;
                default -> { }
            }
        }
        Integer min = cycle.getFeedbackMinReviewers();
        boolean meets = min == null || completed >= min;
        return new NominationSummary(nominated, approved, completed, declined,
                min, cycle.getFeedbackMaxReviewers(), meets);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private FeedbackNomination requireNomination(UUID id) {
        FeedbackNomination n = nominations.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Feedback request not found: " + id));
        if (!accessScope.isAccessible(n.getSubjectEmployeeId())
                && !accessScope.isAccessible(n.getReviewerEmployeeId())) {
            throw new AccessDeniedException("Feedback request is outside your access scope");
        }
        return n;
    }

    private void requireAccessible(UUID employeeId) {
        if (!accessScope.isAccessible(employeeId)) {
            throw new AccessDeniedException("Employee is outside your access scope");
        }
    }

    private static void assertStatus(FeedbackNomination n, String expected) {
        if (!expected.equals(n.getStatus())) {
            throw new BadRequestException("Request must be " + expected + " (was " + n.getStatus() + ")");
        }
    }

    private List<NominationResponse> decorate(List<FeedbackNomination> rows) {
        Map<UUID, String> names = new HashMap<>();
        Map<UUID, String> qNames = new HashMap<>();
        return rows.stream().map(n -> NominationResponse.from(n,
                names.computeIfAbsent(n.getSubjectEmployeeId(), this::employeeName),
                names.computeIfAbsent(n.getReviewerEmployeeId(), this::employeeName),
                n.getQuestionnaireId() == null ? null
                        : qNames.computeIfAbsent(n.getQuestionnaireId(),
                                id -> questionnaires.findById(id)
                                        .map(FeedbackQuestionnaire::getName).orElse(null))))
                .toList();
    }

    private String employeeName(UUID id) {
        return employees.findById(id)
                .map(e -> (e.getFirstName() + " " + e.getLastName()).trim()).orElse(null);
    }
}
