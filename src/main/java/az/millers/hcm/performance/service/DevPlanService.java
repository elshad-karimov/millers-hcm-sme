package az.millers.hcm.performance.service;
import az.millers.hcm.common.tenant.TenantContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
import az.millers.hcm.performance.domain.DevelopmentAction;
import az.millers.hcm.performance.domain.DevelopmentPlan;
import az.millers.hcm.performance.domain.PerfCompetencyAssessment;
import az.millers.hcm.performance.domain.PerformanceReview;
import az.millers.hcm.performance.repo.DevelopmentActionRepository;
import az.millers.hcm.performance.repo.DevelopmentPlanRepository;
import az.millers.hcm.performance.repo.PerfCompetencyAssessmentRepository;
import az.millers.hcm.performance.repo.PerformanceReviewRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.scope.AccessScopeService;
import jakarta.persistence.EntityManager;

/**
 * HCM_12 M399 — development plans (PRD §21). Plans carry typed actions (§21.2);
 * progress = done / live actions. {@code createFromGaps} turns a review's M393
 * competency gaps (§17.3, gap &gt; 0) into a ready-made plan (§21.3).
 */
@Service
public class DevPlanService {

    private static final String MODULE = "PERFORMANCE";
    private static final String ENTITY = "DevelopmentPlan";
    private static final Set<String> ACTION_TYPES = Set.of(
            "TRAINING_COURSE", "CERTIFICATION", "COACHING", "MENTORING", "JOB_ROTATION",
            "STRETCH_ASSIGNMENT", "PROJECT_ASSIGNMENT", "SELF_STUDY", "WORKSHOP",
            "LEADERSHIP_PROGRAM");
    private static final Set<String> ACTION_STATUSES = Set.of(
            "PLANNED", "IN_PROGRESS", "DONE", "CANCELLED");

    public record ActionRequest(UUID id, String actionType, String description, UUID courseId,
                                LocalDate dueDate, String status) {}

    public record PlanRequest(UUID employeeId, UUID reviewId, UUID competencyId, String careerGoal,
                              String title, String description, LocalDate targetDate,
                              UUID ownerEmployeeId, String managerComments, String employeeComments,
                              List<ActionRequest> actions) {}

    private final DevelopmentPlanRepository plans;
    private final DevelopmentActionRepository actions;
    private final PerformanceReviewRepository reviews;
    private final PerfCompetencyAssessmentRepository competencyAssessments;
    private final EmployeeRepository employees;
    private final AccessScopeService accessScope;
    private final AuditService audit;
    private final CurrentRequest currentRequest;
    private final EntityManager entityManager;

    public DevPlanService(DevelopmentPlanRepository plans,
                          DevelopmentActionRepository actions,
                          PerformanceReviewRepository reviews,
                          PerfCompetencyAssessmentRepository competencyAssessments,
                          EmployeeRepository employees,
                          AccessScopeService accessScope,
                          AuditService audit,
                          CurrentRequest currentRequest,
                          EntityManager entityManager) {
        this.plans = plans;
        this.actions = actions;
        this.reviews = reviews;
        this.competencyAssessments = competencyAssessments;
        this.employees = employees;
        this.accessScope = accessScope;
        this.audit = audit;
        this.currentRequest = currentRequest;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<DevelopmentPlan> list(UUID employeeId, String status) {
        List<DevelopmentPlan> rows;
        if (employeeId != null) {
            rows = plans.findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(TenantContext.current(), employeeId);
        } else if (status != null) {
            rows = plans.findByTenantIdAndStatusOrderByCreatedAtDesc(TenantContext.current(), status);
        } else {
            rows = plans.findByTenantIdOrderByCreatedAtDesc(TenantContext.current());
        }
        return rows.stream().filter(p -> accessScope.isAccessible(p.getEmployeeId())).toList();
    }

    @Transactional(readOnly = true)
    public List<DevelopmentAction> actionsFor(UUID planId) {
        requireAccessible(planId);
        return actions.findByPlanIdOrderByDueDateAscDescriptionAsc(planId);
    }

    @Transactional
    public DevelopmentPlan save(UUID id, PlanRequest req) {
        if (req.title() == null || req.title().isBlank()) {
            throw new BadRequestException("A plan title is required");
        }
        DevelopmentPlan p;
        if (id == null) {
            if (!employees.existsById(req.employeeId())) {
                throw new BadRequestException("Employee not found: " + req.employeeId());
            }
            requireEmployeeAccessible(req.employeeId());
            p = new DevelopmentPlan();
            p.setTenantId(TenantContext.current());
            p.setEmployeeId(req.employeeId());
            p.setCreatedBy(currentRequest.username());
        } else {
            p = requireAccessible(id);
            if ("COMPLETED".equals(p.getStatus()) || "CANCELLED".equals(p.getStatus())) {
                throw new BadRequestException("A finished plan cannot be edited");
            }
        }
        p.setReviewId(req.reviewId());
        p.setCompetencyId(req.competencyId());
        p.setCareerGoal(req.careerGoal());
        p.setTitle(req.title().trim());
        p.setDescription(req.description());
        p.setTargetDate(req.targetDate());
        p.setOwnerEmployeeId(req.ownerEmployeeId());
        p.setManagerComments(req.managerComments());
        p.setEmployeeComments(req.employeeComments());
        DevelopmentPlan saved = plans.save(p);

        // Full-replace actions (delete + flush + reinsert — the M300 lesson).
        actions.deleteByPlanId(saved.getId());
        entityManager.flush();
        if (req.actions() != null) {
            for (ActionRequest ar : req.actions()) {
                if (ar.description() == null || ar.description().isBlank()) continue;
                if (ar.actionType() != null && !ACTION_TYPES.contains(ar.actionType())) {
                    throw new BadRequestException("Unknown action type: " + ar.actionType());
                }
                if (ar.status() != null && !ACTION_STATUSES.contains(ar.status())) {
                    throw new BadRequestException("Unknown action status: " + ar.status());
                }
                DevelopmentAction a = new DevelopmentAction();
                a.setTenantId(TenantContext.current());
                a.setPlanId(saved.getId());
                a.setActionType(ar.actionType() == null ? "TRAINING_COURSE" : ar.actionType());
                a.setDescription(ar.description().trim());
                a.setCourseId(ar.courseId());
                a.setDueDate(ar.dueDate());
                a.setStatus(ar.status() == null ? "PLANNED" : ar.status());
                if ("DONE".equals(a.getStatus())) a.setCompletedAt(OffsetDateTime.now());
                actions.save(a);
            }
        }
        recomputeProgress(saved);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                id == null ? "CREATE" : "UPDATE", null, req.title().trim());
        return plans.save(saved);
    }

    /** §21.3 — build a plan from the review's positive competency gaps (M393). */
    @Transactional
    public DevelopmentPlan createFromGaps(UUID reviewId) {
        PerformanceReview review = reviews.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found: " + reviewId));
        requireEmployeeAccessible(review.getEmployeeId());
        List<PerfCompetencyAssessment> gaps = competencyAssessments
                .findByReviewIdOrderByCreatedAtAsc(reviewId).stream()
                .filter(a -> a.getGap() != null && a.getGap() > 0)
                .toList();
        if (gaps.isEmpty()) {
            throw new BadRequestException("The review has no positive competency gaps to plan for");
        }
        DevelopmentPlan p = new DevelopmentPlan();
        p.setTenantId(TenantContext.current());
        p.setEmployeeId(review.getEmployeeId());
        p.setReviewId(reviewId);
        p.setTitle("Close competency gaps — review " + review.getReviewNo());
        p.setDescription("Auto-generated from the review's competency assessment (§21.3).");
        p.setCreatedBy(currentRequest.username());
        DevelopmentPlan saved = plans.save(p);
        List<DevelopmentAction> created = new ArrayList<>();
        for (PerfCompetencyAssessment g : gaps) {
            DevelopmentAction a = new DevelopmentAction();
            a.setTenantId(TenantContext.current());
            a.setPlanId(saved.getId());
            a.setActionType("TRAINING_COURSE");
            a.setDescription("Close competency gap (level " + g.getFinalLevel()
                    + " → " + g.getRequiredLevel() + ") — competency " + g.getCompetencyId());
            created.add(actions.save(a));
        }
        recomputeProgress(saved);
        audit.record(MODULE, ENTITY, saved.getId().toString(), "CREATE_FROM_GAPS",
                null, Map.of("reviewId", reviewId.toString(), "actions", String.valueOf(created.size())));
        return plans.save(saved);
    }

    @Transactional
    public DevelopmentPlan changeStatus(UUID id, String status) {
        if (!Set.of("DRAFT", "ACTIVE", "IN_PROGRESS", "COMPLETED", "CANCELLED").contains(status)) {
            throw new BadRequestException("Unknown plan status: " + status);
        }
        DevelopmentPlan p = requireAccessible(id);
        String before = p.getStatus();
        p.setStatus(status);
        audit.record(MODULE, ENTITY, id.toString(), "STATUS", before, status);
        return plans.save(p);
    }

    /** Mark a single action; plan progress + status roll up. */
    @Transactional
    public DevelopmentPlan markAction(UUID actionId, String status) {
        if (!ACTION_STATUSES.contains(status)) {
            throw new BadRequestException("Unknown action status: " + status);
        }
        DevelopmentAction a = actions.findById(actionId)
                .orElseThrow(() -> new ResourceNotFoundException("Action not found: " + actionId));
        DevelopmentPlan p = requireAccessible(a.getPlanId());
        a.setStatus(status);
        a.setCompletedAt("DONE".equals(status) ? OffsetDateTime.now() : null);
        actions.save(a);
        recomputeProgress(p);
        if ("DRAFT".equals(p.getStatus()) || "ACTIVE".equals(p.getStatus())) {
            p.setStatus("IN_PROGRESS");
        }
        if (p.getProgressPercent().compareTo(BigDecimal.valueOf(100)) == 0) {
            p.setStatus("COMPLETED");
        }
        audit.record(MODULE, "DevelopmentAction", actionId.toString(), "MARK", null, status);
        return plans.save(p);
    }

    private void recomputeProgress(DevelopmentPlan p) {
        List<DevelopmentAction> all = actions.findByPlanIdOrderByDueDateAscDescriptionAsc(p.getId());
        long live = all.stream().filter(a -> !"CANCELLED".equals(a.getStatus())).count();
        long done = all.stream().filter(a -> "DONE".equals(a.getStatus())).count();
        p.setProgressPercent(live == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(done * 100).divide(BigDecimal.valueOf(live), 2, RoundingMode.HALF_UP));
    }

    private DevelopmentPlan requireAccessible(UUID id) {
        DevelopmentPlan p = plans.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Development plan not found: " + id));
        requireEmployeeAccessible(p.getEmployeeId());
        return p;
    }

    private void requireEmployeeAccessible(UUID employeeId) {
        if (!accessScope.isAccessible(employeeId)) {
            throw new AccessDeniedException("Development plan is outside your access scope");
        }
    }
}
