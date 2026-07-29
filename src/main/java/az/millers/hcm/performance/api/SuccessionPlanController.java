package az.millers.hcm.performance.api;
import az.millers.hcm.common.tenant.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.performance.domain.CriticalPosition;
import az.millers.hcm.performance.domain.SuccessionPlan;
import az.millers.hcm.performance.repo.CriticalPositionRepository;
import az.millers.hcm.performance.repo.SuccessionPlanRepository;
import az.millers.hcm.performance.service.SuccessionPlanApprovalService;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.SecurityRoles;

/**
 * HCM_16 M413 — critical positions + succession plans + approval
 * (PRD §16.2/§16.3/§16.4). Thin CRUD. HR-only (GLOBAL RULE 17).
 */
@RestController
@RequestMapping("/api/succession")
public class SuccessionPlanController {

    private static final String MODULE = "SUCCESSION";
    private static final List<String> DIFFICULTIES = List.of("LOW", "MEDIUM", "HIGH");
    private static final List<String> RISKS = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final List<String> STATUSES = List.of("DRAFT", "SUBMITTED", "APPROVED", "ACTIVE", "ARCHIVED");

    public record CriticalPosRequest(UUID positionId, String criticalityReason,
                                     String replacementDifficulty, String vacancyRisk,
                                     Boolean successionRequired, Boolean active) {}

    public record PlanRequest(UUID criticalPositionId, UUID planOwnerEmployeeId,
                              LocalDate effectiveDate, LocalDate reviewDate,
                              UUID emergencySuccessorEmployeeId, String notes) {}

    private final CriticalPositionRepository critPositions;
    private final SuccessionPlanRepository plans;
    private final EmployeeRepository employees;
    private final SuccessionPlanApprovalService approvalService;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public SuccessionPlanController(CriticalPositionRepository critPositions,
                                    SuccessionPlanRepository plans,
                                    EmployeeRepository employees,
                                    SuccessionPlanApprovalService approvalService,
                                    AuditService audit,
                                    CurrentRequest currentRequest) {
        this.critPositions = critPositions;
        this.plans = plans;
        this.employees = employees;
        this.approvalService = approvalService;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @GetMapping("/critical-positions")
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<CriticalPosition> listCritical(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return activeOnly
                ? critPositions.findByTenantIdAndActiveTrueOrderByCreatedAtDesc(TenantContext.current())
                : critPositions.findByTenantIdOrderByCreatedAtDesc(TenantContext.current());
    }

    @PostMapping("/critical-positions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    @Transactional
    public CriticalPosition createCritical(@RequestBody CriticalPosRequest req) {
        if (req.positionId() == null) {
            throw new BadRequestException("Position ID is required");
        }
        if (critPositions.existsByTenantIdAndPositionId(TenantContext.current(), req.positionId())) {
            throw new BadRequestException("Position is already critical: " + req.positionId());
        }
        CriticalPosition cp = new CriticalPosition();
        cp.setTenantId(TenantContext.current());
        cp.setPositionId(req.positionId());
        cp.setCriticalityReason(req.criticalityReason());
        cp.setReplacementDifficulty(req.replacementDifficulty());
        cp.setVacancyRisk(req.vacancyRisk());
        if (req.successionRequired() != null) cp.setSuccessionRequired(req.successionRequired());
        if (req.active() != null) cp.setActive(req.active());
        cp.setCreatedBy(currentRequest.username());
        CriticalPosition saved = critPositions.save(cp);
        audit.record(MODULE, "CriticalPosition", saved.getId().toString(),
                "CREATE", null, req.positionId().toString());
        return saved;
    }

    @GetMapping("/plans")
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<SuccessionPlan> listPlans(@RequestParam(required = false) String status) {
        return status == null
                ? plans.findByTenantIdOrderByUpdatedAtDesc(TenantContext.current())
                : plans.findByTenantIdAndStatusOrderByUpdatedAtDesc(TenantContext.current(), status);
    }

    @PostMapping("/plans")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    @Transactional
    public SuccessionPlan createPlan(@RequestBody PlanRequest req) {
        if (req.criticalPositionId() == null || !critPositions.existsById(req.criticalPositionId())) {
            throw new BadRequestException("Critical position not found: " + req.criticalPositionId());
        }
        SuccessionPlan plan = new SuccessionPlan();
        plan.setTenantId(TenantContext.current());
        plan.setCriticalPositionId(req.criticalPositionId());
        plan.setPlanOwnerEmployeeId(req.planOwnerEmployeeId());
        plan.setEffectiveDate(req.effectiveDate());
        plan.setReviewDate(req.reviewDate());
        plan.setEmergencySuccessorEmployeeId(req.emergencySuccessorEmployeeId());
        plan.setNotes(req.notes());
        plan.setCreatedBy(currentRequest.username());
        SuccessionPlan saved = plans.save(plan);
        audit.record(MODULE, "SuccessionPlan", saved.getId().toString(), "CREATE", null, req.criticalPositionId().toString());
        return saved;
    }

    @PostMapping("/plans/{id}/submit")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public SuccessionPlan submitPlan(@PathVariable UUID id) {
        return approvalService.submit(id, currentRequest.username());
    }

    @PutMapping("/plans/{id}/status")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    @Transactional
    public SuccessionPlan updatePlanStatus(@PathVariable UUID id, @RequestParam String status) {
        if (!STATUSES.contains(status)) {
            throw new BadRequestException("Status must be one of " + STATUSES);
        }
        SuccessionPlan plan = plans.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Succession plan not found: " + id));
        String before = plan.getStatus();
        plan.setStatus(status);
        SuccessionPlan saved = plans.save(plan);
        audit.record(MODULE, "SuccessionPlan", id.toString(), "STATUS", before, status);
        return saved;
    }
}
