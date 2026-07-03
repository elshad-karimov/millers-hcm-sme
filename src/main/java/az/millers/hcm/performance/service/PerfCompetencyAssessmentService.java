package az.millers.hcm.performance.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.learning.domain.Competency;
import az.millers.hcm.learning.repo.CompetencyRepository;
import az.millers.hcm.performance.api.dto.CompetencyAssessmentDtos.AddCompetencyRequest;
import az.millers.hcm.performance.api.dto.CompetencyAssessmentDtos.AssessmentResponse;
import az.millers.hcm.performance.api.dto.CompetencyAssessmentDtos.RateRequest;
import az.millers.hcm.performance.domain.PerfCompetencyAssessment;
import az.millers.hcm.performance.domain.PerformanceReview;
import az.millers.hcm.performance.repo.PerfCompetencyAssessmentRepository;
import az.millers.hcm.performance.repo.PerformanceReviewRepository;
import az.millers.hcm.security.scope.AccessScopeService;
import az.millers.hcm.staffing.repo.PositionRequiredCompetencyRepository;

/**
 * HCM_12 M393 — structured competency assessment per review (PRD §17).
 *
 * <p>{@code init} seeds one row per {@code position_required_competency} of the
 * employee's position (required level snapshotted); rows can also be added
 * manually. §17.3: gap = required − final, recomputed on every rating change —
 * a positive gap is a development need (M399 dev plans read this).
 */
@Service
public class PerfCompetencyAssessmentService {

    private static final String MODULE = "PERFORMANCE";
    private static final String ENTITY = "PerfCompetencyAssessment";

    private final PerfCompetencyAssessmentRepository assessments;
    private final PerformanceReviewRepository reviews;
    private final PositionRequiredCompetencyRepository positionRequirements;
    private final CompetencyRepository competencies;
    private final EmployeeRepository employees;
    private final AccessScopeService accessScope;
    private final AuditService audit;

    public PerfCompetencyAssessmentService(PerfCompetencyAssessmentRepository assessments,
                                           PerformanceReviewRepository reviews,
                                           PositionRequiredCompetencyRepository positionRequirements,
                                           CompetencyRepository competencies,
                                           EmployeeRepository employees,
                                           AccessScopeService accessScope,
                                           AuditService audit) {
        this.assessments = assessments;
        this.reviews = reviews;
        this.positionRequirements = positionRequirements;
        this.competencies = competencies;
        this.employees = employees;
        this.accessScope = accessScope;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<AssessmentResponse> list(UUID reviewId) {
        requireAccessibleReview(reviewId);
        return decorate(assessments.findByReviewIdOrderByCreatedAtAsc(reviewId));
    }

    /** Seed rows from the employee's position requirements (idempotent — skips existing). */
    @Transactional
    public List<AssessmentResponse> init(UUID reviewId) {
        PerformanceReview review = requireAccessibleReview(reviewId);
        UUID positionId = employees.findById(review.getEmployeeId())
                .map(e -> e.getPositionId()).orElse(null);
        if (positionId == null) {
            throw new BadRequestException(
                    "Employee has no position — add competencies manually instead");
        }
        var required = positionRequirements
                .findByPositionIdOrderByMandatoryDescRequiredLevelDesc(positionId);
        if (required.isEmpty()) {
            throw new BadRequestException(
                    "The position defines no required competencies — add competencies manually instead");
        }
        List<PerfCompetencyAssessment> created = new ArrayList<>();
        for (var prc : required) {
            if (assessments.existsByReviewIdAndCompetencyId(reviewId, prc.getCompetencyId())) continue;
            PerfCompetencyAssessment a = new PerfCompetencyAssessment();
            a.setTenantId(review.getTenantId());
            a.setReviewId(reviewId);
            a.setCompetencyId(prc.getCompetencyId());
            a.setRequiredLevel(prc.getRequiredLevel());
            created.add(a);
        }
        if (!created.isEmpty()) {
            assessments.saveAll(created);
            audit.record(MODULE, ENTITY, reviewId.toString(), "INIT",
                    null, Map.of("seeded", String.valueOf(created.size())));
        }
        return decorate(assessments.findByReviewIdOrderByCreatedAtAsc(reviewId));
    }

    @Transactional
    public AssessmentResponse add(UUID reviewId, AddCompetencyRequest req) {
        PerformanceReview review = requireAccessibleReview(reviewId);
        if (!competencies.existsById(req.competencyId())) {
            throw new BadRequestException("Competency not found: " + req.competencyId());
        }
        if (assessments.existsByReviewIdAndCompetencyId(reviewId, req.competencyId())) {
            throw new BadRequestException("This competency is already on the review");
        }
        PerfCompetencyAssessment a = new PerfCompetencyAssessment();
        a.setTenantId(review.getTenantId());
        a.setReviewId(reviewId);
        a.setCompetencyId(req.competencyId());
        a.setRequiredLevel(req.requiredLevel());
        PerfCompetencyAssessment saved = assessments.save(a);
        audit.record(MODULE, ENTITY, saved.getId().toString(), "ADD",
                null, req.competencyId().toString());
        return decorate(List.of(saved)).get(0);
    }

    /** Record self/manager/final levels; §17.3 gap recomputed from required − final. */
    @Transactional
    public AssessmentResponse rate(UUID assessmentId, RateRequest req) {
        PerfCompetencyAssessment a = assessments.findById(assessmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assessment not found: " + assessmentId));
        requireAccessibleReview(a.getReviewId());
        String before = levels(a);
        if (req.selfLevel() != null) a.setSelfLevel(req.selfLevel());
        if (req.managerLevel() != null) a.setManagerLevel(req.managerLevel());
        if (req.finalLevel() != null) a.setFinalLevel(req.finalLevel());
        if (req.comment() != null) a.setComment(req.comment());
        a.setGap(a.getRequiredLevel() != null && a.getFinalLevel() != null
                ? a.getRequiredLevel() - a.getFinalLevel() : null);
        PerfCompetencyAssessment saved = assessments.save(a);
        audit.record(MODULE, ENTITY, assessmentId.toString(), "RATE", before, levels(saved));
        return decorate(List.of(saved)).get(0);
    }

    private static String levels(PerfCompetencyAssessment a) {
        return "self=" + a.getSelfLevel() + " mgr=" + a.getManagerLevel()
                + " final=" + a.getFinalLevel() + " gap=" + a.getGap();
    }

    private PerformanceReview requireAccessibleReview(UUID reviewId) {
        PerformanceReview review = reviews.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found: " + reviewId));
        // GLOBAL RULE 7/8 — employees see only their own review, managers only their team.
        if (!accessScope.isAccessible(review.getEmployeeId())) {
            throw new AccessDeniedException("Review is outside your access scope");
        }
        return review;
    }

    private List<AssessmentResponse> decorate(List<PerfCompetencyAssessment> rows) {
        Map<UUID, Competency> cache = new HashMap<>();
        return rows.stream().map(a -> AssessmentResponse.from(a,
                cache.computeIfAbsent(a.getCompetencyId(), id -> competencies.findById(id).orElse(null))))
                .toList();
    }
}
