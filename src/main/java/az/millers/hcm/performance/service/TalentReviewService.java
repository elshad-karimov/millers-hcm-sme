package az.millers.hcm.performance.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.performance.domain.TalentProfile;
import az.millers.hcm.performance.domain.TalentReview;
import az.millers.hcm.performance.domain.TalentReviewCycle;
import az.millers.hcm.performance.repo.TalentProfileRepository;
import az.millers.hcm.performance.repo.TalentReviewCycleRepository;
import az.millers.hcm.performance.repo.TalentReviewRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * HCM_15 M411 — talent review cycles + panel decisions (PRD §15.7/§15.8).
 * Recording a review upserts the employee's talent_profile (M409) with the
 * HiPo + retention decisions (analysis.md gap-check defaults).
 */
@Service
public class TalentReviewService {

    private static final String TENANT = "default";
    private static final String MODULE = "TALENT";
    private static final List<String> STATUSES = List.of("DRAFT", "ACTIVE", "COMPLETED");
    private static final List<String> RISKS = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    private final TalentReviewCycleRepository cycles;
    private final TalentReviewRepository reviews;
    private final TalentProfileRepository profiles;
    private final EmployeeRepository employees;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public TalentReviewService(TalentReviewCycleRepository cycles,
                               TalentReviewRepository reviews,
                               TalentProfileRepository profiles,
                               EmployeeRepository employees,
                               AuditService audit,
                               CurrentRequest currentRequest) {
        this.cycles = cycles;
        this.reviews = reviews;
        this.profiles = profiles;
        this.employees = employees;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    // ── Cycles ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TalentReviewCycle> listCycles(String status) {
        return status == null
                ? cycles.findByTenantIdOrderByYearDesc(TENANT)
                : cycles.findByTenantIdAndStatusOrderByYearDesc(TENANT, status);
    }

    @Transactional
    public TalentReviewCycle createCycle(String name, int year) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("Cycle name is required");
        }
        TalentReviewCycle cycle = new TalentReviewCycle();
        cycle.setTenantId(TENANT);
        cycle.setName(name.trim());
        cycle.setYear(year);
        cycle.setCreatedBy(currentRequest.username());
        TalentReviewCycle saved = cycles.save(cycle);
        audit.record(MODULE, "TalentReviewCycle", saved.getId().toString(),
                "CREATE", null, name.trim());
        return saved;
    }

    @Transactional
    public TalentReviewCycle updateCycleStatus(UUID cycleId, String status) {
        if (!STATUSES.contains(status)) {
            throw new BadRequestException("Status must be one of " + STATUSES);
        }
        TalentReviewCycle cycle = cycles.findById(cycleId)
                .orElseThrow(() -> new ResourceNotFoundException("Cycle not found: " + cycleId));
        String before = cycle.getStatus();
        cycle.setStatus(status);
        TalentReviewCycle saved = cycles.save(cycle);
        audit.record(MODULE, "TalentReviewCycle", cycleId.toString(),
                "STATUS", before, status);
        return saved;
    }

    // ── Reviews ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TalentReview> listReviews(UUID cycleId) {
        if (!cycles.existsById(cycleId)) {
            throw new ResourceNotFoundException("Cycle not found: " + cycleId);
        }
        return reviews.findByCycleIdOrderByDecidedAtDesc(cycleId);
    }

    /**
     * Record (upsert) a talent review for an employee in a cycle. ON RECORD,
     * roll the HiPo + retention decision onto the employee's talent_profile
     * (M409 upsert) — single source of truth for current HiPo/retention status.
     */
    @Transactional
    public TalentReview recordReview(UUID cycleId, UUID employeeId,
                                     Integer performanceBox, Integer potentialBox,
                                     Boolean hipoDecision, String retentionRisk,
                                     String retentionAction, String notes) {
        if (!cycles.existsById(cycleId)) {
            throw new ResourceNotFoundException("Cycle not found: " + cycleId);
        }
        if (!employees.existsById(employeeId)) {
            throw new BadRequestException("Employee not found: " + employeeId);
        }
        if (performanceBox != null && (performanceBox < 1 || performanceBox > 3)) {
            throw new BadRequestException("Performance box must be 1..3");
        }
        if (potentialBox != null && (potentialBox < 1 || potentialBox > 3)) {
            throw new BadRequestException("Potential box must be 1..3");
        }
        if (retentionRisk != null && !RISKS.contains(retentionRisk)) {
            throw new BadRequestException("Retention risk must be one of " + RISKS);
        }

        TalentReview review = reviews.findByCycleIdAndEmployeeId(cycleId, employeeId)
                .orElseGet(() -> {
                    TalentReview r = new TalentReview();
                    r.setTenantId(TENANT);
                    r.setCycleId(cycleId);
                    r.setEmployeeId(employeeId);
                    return r;
                });

        review.setPerformanceBox(performanceBox);
        review.setPotentialBox(potentialBox);
        review.setHipoDecision(hipoDecision != null && hipoDecision);
        review.setRetentionRisk(retentionRisk);
        review.setRetentionAction(retentionAction);
        review.setNotes(notes);
        review.setDecidedBy(currentRequest.username());
        review.setDecidedAt(OffsetDateTime.now());
        TalentReview saved = reviews.save(review);

        // Roll decision onto talent_profile (M409 upsert).
        TalentProfile profile = profiles.findByTenantIdAndEmployeeId(TENANT, employeeId)
                .orElseGet(() -> {
                    TalentProfile p = new TalentProfile();
                    p.setTenantId(TENANT);
                    p.setEmployeeId(employeeId);
                    return p;
                });
        profile.setHipoFlag(saved.isHipoDecision());
        profile.setHipoSource(saved.isHipoDecision() ? "NINE_BOX" : null);
        profile.setRetentionRisk(saved.getRetentionRisk());
        profile.setRetentionActionPlan(saved.getRetentionAction());
        profile.setUpdatedBy(currentRequest.username());
        profiles.save(profile);

        audit.record(MODULE, "TalentReview", saved.getId().toString(),
                "RECORD", null, employeeId.toString());
        return saved;
    }
}
