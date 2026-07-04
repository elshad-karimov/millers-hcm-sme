package az.millers.hcm.performance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.performance.api.dto.CalibrationRequest;
import az.millers.hcm.performance.api.dto.ManagerReviewRequest;
import az.millers.hcm.performance.api.dto.PerformanceReviewResponse;
import az.millers.hcm.performance.api.dto.ReviewStartRequest;
import az.millers.hcm.performance.api.dto.SelfReviewRequest;
import az.millers.hcm.performance.domain.CycleStatus;
import az.millers.hcm.performance.domain.Goal;
import az.millers.hcm.performance.domain.PerformanceReview;
import az.millers.hcm.performance.domain.ReviewCycle;
import az.millers.hcm.performance.domain.ReviewStatus;
import az.millers.hcm.performance.repo.GoalRepository;
import az.millers.hcm.performance.repo.PerformanceReviewRepository;
import az.millers.hcm.performance.repo.ReviewCycleRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.workflow.api.dto.StartWorkflowRequest;
import az.millers.hcm.workflow.domain.WorkflowInstance;
import az.millers.hcm.workflow.service.WorkflowService;

/**
 * Performance Review service (PRD 8.13.4).
 *
 * <p>Lifecycle:
 * DRAFT → SELF_IN_PROGRESS → SELF_SUBMITTED → MANAGER_IN_PROGRESS →
 * MANAGER_SUBMITTED → PENDING_APPROVAL (workflow PERFORMANCE_REVIEW_APPROVAL) →
 * APPROVED → COMPLETED (closed). Or REJECTED / CANCELLED at any pre-approval step.
 *
 * <p>The goal score is the weighted average of per-goal ratings (auditor trace).
 * The final rating is set by HR during calibration and ultimately drives the
 * bonus matrix (PRD 8.13.7) via the recommendation + bonusPercent fields.
 */
@Service
public class PerformanceReviewService {

    public static final String WORKFLOW_DEFINITION = "PERFORMANCE_REVIEW_APPROVAL";
    private static final String MODULE = "PERFORMANCE";
    private static final String ENTITY = "PerformanceReview";

    private final PerformanceReviewRepository reviews;
    private final ReviewCycleRepository cycles;
    private final GoalRepository goals;
    private final EmployeeRepository employees;
    private final WorkflowService workflowService;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public PerformanceReviewService(PerformanceReviewRepository reviews,
                                     ReviewCycleRepository cycles,
                                     GoalRepository goals,
                                     EmployeeRepository employees,
                                     WorkflowService workflowService,
                                     AuditService audit,
                                     CurrentRequest currentRequest) {
        this.reviews = reviews;
        this.cycles = cycles;
        this.goals = goals;
        this.employees = employees;
        this.workflowService = workflowService;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public PerformanceReview get(UUID id) {
        return reviews.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<PerformanceReview> list(UUID cycleId, UUID employeeId, ReviewStatus status,
                                          Pageable pageable) {
        if (cycleId != null)    return reviews.findByCycleIdOrderByCreatedAtDesc(cycleId, pageable);
        if (employeeId != null) return reviews.findByEmployeeIdOrderByCreatedAtDesc(employeeId, pageable);
        if (status != null)     return reviews.findByStatusOrderByCreatedAtDesc(status, pageable);
        return reviews.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional
    public PerformanceReview start(ReviewStartRequest req) {
        ReviewCycle cycle = cycles.findById(req.cycleId())
                .orElseThrow(() -> new BadRequestException("Cycle not found: " + req.cycleId()));
        if (cycle.getStatus() != CycleStatus.OPEN && cycle.getStatus() != CycleStatus.CALIBRATING) {
            throw new BadRequestException(
                    "Cycle must be OPEN or CALIBRATING to start reviews (was " + cycle.getStatus() + ")");
        }
        Employee employee = employees.findById(req.employeeId())
                .orElseThrow(() -> new BadRequestException("Employee not found: " + req.employeeId()));
        reviews.findByCycleIdAndEmployeeId(req.cycleId(), req.employeeId())
                .ifPresent(r -> { throw new BadRequestException(
                        "Review already exists for this employee in this cycle: " + r.getReviewNo()); });

        PerformanceReview r = new PerformanceReview();
        r.setReviewNo(String.format("REV-%05d", reviews.nextNoSequence()));
        r.setCycleId(cycle.getId());
        r.setEmployeeId(employee.getId());
        r.setManagerId(req.managerId() != null ? req.managerId() : employee.getManagerId());
        r.setStatus(ReviewStatus.SELF_IN_PROGRESS);
        r.setCreatedBy(currentRequest.username());
        PerformanceReview saved = reviews.save(r);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "START", null, PerformanceReviewResponse.from(saved));
        return saved;
    }

    @Transactional
    public PerformanceReview submitSelfReview(UUID id, SelfReviewRequest req) {
        PerformanceReview r = get(id);
        if (r.getStatus() != ReviewStatus.SELF_IN_PROGRESS && r.getStatus() != ReviewStatus.DRAFT) {
            throw new BadRequestException(
                    "Self-review can only be submitted in DRAFT/SELF_IN_PROGRESS (was " + r.getStatus() + ")");
        }
        validateRating(req.rating(), "self rating");
        PerformanceReviewResponse before = PerformanceReviewResponse.from(r);
        r.setSelfRating(req.rating());
        r.setSelfComments(req.comments());
        r.setSelfSubmittedAt(OffsetDateTime.now());
        r.setStatus(ReviewStatus.SELF_SUBMITTED);
        PerformanceReview saved = reviews.save(r);
        audit.record(MODULE, ENTITY, id.toString(),
                "SELF_SUBMIT", before, PerformanceReviewResponse.from(saved));
        return saved;
    }

    @Transactional
    public PerformanceReview submitManagerReview(UUID id, ManagerReviewRequest req) {
        PerformanceReview r = get(id);
        rejectIfCalibrationLocked(r);
        if (r.getStatus() != ReviewStatus.SELF_SUBMITTED
                && r.getStatus() != ReviewStatus.MANAGER_IN_PROGRESS) {
            throw new BadRequestException(
                    "Manager review requires self submission first (was " + r.getStatus() + ")");
        }
        validateRating(req.rating(), "manager rating");
        PerformanceReviewResponse before = PerformanceReviewResponse.from(r);
        r.setManagerRating(req.rating());
        r.setManagerComments(req.comments());
        r.setManagerSubmittedAt(OffsetDateTime.now());
        // Compute the cached goal score now that the manager has signed off.
        r.setGoalScore(computeGoalScore(r));
        r.setStatus(ReviewStatus.MANAGER_SUBMITTED);
        PerformanceReview saved = reviews.save(r);
        audit.record(MODULE, ENTITY, id.toString(),
                "MANAGER_SUBMIT", before, PerformanceReviewResponse.from(saved));
        return saved;
    }

    @Transactional
    public PerformanceReview submitForApproval(UUID id) {
        PerformanceReview r = get(id);
        if (r.getStatus() != ReviewStatus.MANAGER_SUBMITTED) {
            throw new BadRequestException(
                    "Review must be MANAGER_SUBMITTED to start approval (was " + r.getStatus() + ")");
        }
        PerformanceReviewResponse before = PerformanceReviewResponse.from(r);
        ReviewCycle cycle = cycles.findById(r.getCycleId())
                .orElseThrow(() -> new ResourceNotFoundException("Cycle missing: " + r.getCycleId()));
        Employee employee = employees.findById(r.getEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee missing: " + r.getEmployeeId()));

        WorkflowInstance instance = workflowService.start(new StartWorkflowRequest(
                WORKFLOW_DEFINITION,
                MODULE,
                ENTITY,
                r.getId().toString(),
                r.getReviewNo() + " — " + employee.getFirstName() + " " + employee.getLastName()
                        + " (" + cycle.getCode() + ")",
                Map.of(
                        "employeeNo", employee.getEmployeeNo(),
                        "employeeName", employee.getFirstName() + " " + employee.getLastName(),
                        "cycle", cycle.getCode(),
                        "selfRating", String.valueOf(r.getSelfRating()),
                        "managerRating", String.valueOf(r.getManagerRating()),
                        "goalScore", String.valueOf(r.getGoalScore()))));
        r.setWorkflowInstanceId(instance.getId());
        r.setStatus(ReviewStatus.PENDING_APPROVAL);
        PerformanceReview saved = reviews.save(r);
        audit.record(MODULE, ENTITY, id.toString(),
                "SUBMIT_APPROVAL", before, PerformanceReviewResponse.from(saved));
        return saved;
    }

    @Transactional
    public PerformanceReview calibrate(UUID id, CalibrationRequest req) {
        PerformanceReview r = get(id);
        rejectIfCalibrationLocked(r);
        if (r.getStatus() != ReviewStatus.APPROVED
                && r.getStatus() != ReviewStatus.PENDING_APPROVAL
                && r.getStatus() != ReviewStatus.CALIBRATING) {
            throw new BadRequestException(
                    "Calibration is only allowed in PENDING_APPROVAL / CALIBRATING / APPROVED states");
        }
        if (req.finalRating() != null) validateRating(req.finalRating(), "final rating");
        PerformanceReviewResponse before = PerformanceReviewResponse.from(r);
        if (req.finalRating() != null)     r.setFinalRating(req.finalRating());
        if (req.finalBand() != null)       r.setFinalBand(req.finalBand());
        if (req.recommendation() != null)  r.setRecommendation(req.recommendation());
        if (req.bonusPercent() != null)    r.setBonusPercent(req.bonusPercent());
        if (req.calibrationNotes() != null) r.setCalibrationNotes(req.calibrationNotes());
        r.setStatus(ReviewStatus.CALIBRATING);
        PerformanceReview saved = reviews.save(r);
        audit.record(MODULE, ENTITY, id.toString(),
                "CALIBRATE", before, PerformanceReviewResponse.from(saved));
        return saved;
    }

    @Transactional
    public PerformanceReview close(UUID id) {
        PerformanceReview r = get(id);
        if (r.getStatus() != ReviewStatus.APPROVED && r.getStatus() != ReviewStatus.CALIBRATING) {
            throw new BadRequestException("Only APPROVED / CALIBRATING reviews can be closed");
        }
        if (r.getFinalRating() == null) {
            throw new BadRequestException("Cannot close a review without a finalRating");
        }
        PerformanceReviewResponse before = PerformanceReviewResponse.from(r);
        r.setStatus(ReviewStatus.COMPLETED);
        r.setClosedAt(OffsetDateTime.now());
        PerformanceReview saved = reviews.save(r);
        audit.record(MODULE, ENTITY, id.toString(),
                "COMPLETE", before, PerformanceReviewResponse.from(saved));
        return saved;
    }

    // ---------- Workflow callbacks ----------

    @Transactional
    public PerformanceReview onApproved(UUID id, String comment) {
        PerformanceReview r = get(id);
        if (r.getStatus() != ReviewStatus.PENDING_APPROVAL) return r;
        r.setStatus(ReviewStatus.APPROVED);
        PerformanceReview saved = reviews.save(r);
        audit.record(MODULE, ENTITY, id.toString(), "APPROVED", null,
                Map.of("comment", comment == null ? "" : comment));
        return saved;
    }

    @Transactional
    public PerformanceReview onRejected(UUID id, String comment) {
        PerformanceReview r = get(id);
        if (r.getStatus() != ReviewStatus.PENDING_APPROVAL) return r;
        r.setStatus(ReviewStatus.REJECTED);
        PerformanceReview saved = reviews.save(r);
        audit.record(MODULE, ENTITY, id.toString(), "REJECTED", null,
                Map.of("comment", comment == null ? "" : comment));
        return saved;
    }

    @Transactional
    public PerformanceReview onCancelled(UUID id, String comment) {
        PerformanceReview r = get(id);
        if (r.getStatus() != ReviewStatus.PENDING_APPROVAL) return r;
        r.setStatus(ReviewStatus.CANCELLED);
        PerformanceReview saved = reviews.save(r);
        audit.record(MODULE, ENTITY, id.toString(), "CANCELLED", null,
                Map.of("comment", comment == null ? "" : comment));
        return saved;
    }

    // ---------- Internals ----------

    /**
     * Weighted goal score: Σ(rating × weightPercent) / Σ(weightPercent). When no
     * goals are rated yet, returns null (so the UI can show "— pending"); when
     * weights sum to zero we fall back to a flat average to avoid divide-by-zero.
     */
    private BigDecimal computeGoalScore(PerformanceReview r) {
        List<Goal> all = goals.findByCycleIdAndEmployeeIdOrderByCreatedAt(
                r.getCycleId(), r.getEmployeeId());
        if (all.isEmpty()) return null;
        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal weightTotal = BigDecimal.ZERO;
        int rated = 0;
        BigDecimal flatSum = BigDecimal.ZERO;
        for (Goal g : all) {
            if (g.getRating() == null) continue;
            BigDecimal w = g.getWeightPercent() == null ? BigDecimal.ZERO : g.getWeightPercent();
            weightedSum = weightedSum.add(g.getRating().multiply(w));
            weightTotal = weightTotal.add(w);
            flatSum = flatSum.add(g.getRating());
            rated++;
        }
        if (rated == 0) return null;
        if (weightTotal.signum() == 0) {
            return flatSum.divide(BigDecimal.valueOf(rated), 3, RoundingMode.HALF_UP);
        }
        return weightedSum.divide(weightTotal, 3, RoundingMode.HALF_UP);
    }

    private void validateRating(BigDecimal rating, String label) {
        if (rating == null) {
            throw new BadRequestException(label + " is required");
        }
        if (rating.signum() < 0 || rating.compareTo(BigDecimal.valueOf(5)) > 0) {
            throw new BadRequestException(label + " must be between 0 and 5");
        }
    }

    /**
     * M121 — block any write path on a review that a calibration
     * session has sealed. HR can call
     * {@link CalibrationSessionService#unlockReview} to reopen it.
     */
    private static void rejectIfCalibrationLocked(PerformanceReview r) {
        if (r.isCalibrationLocked()) {
            throw new BadRequestException(
                    "Review " + r.getReviewNo()
                            + " is locked by a completed calibration session — "
                            + "ask HR to unlock before further edits");
        }
    }
}
