package az.millers.hcm.ehs.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.security.access.AccessDeniedException;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.ehs.domain.ReturnToWorkPlan;
import az.millers.hcm.ehs.domain.ReturnToWorkStatus;
import az.millers.hcm.ehs.repo.ReturnToWorkPlanRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.scope.AccessScopeService;

/**
 * M449 — Return-to-work plan service.
 * manager_approved settable by employee's manager (AccessScopeService).
 * hr_approved settable by HR_ADMIN only.
 * Reads restricted to HR_ADMIN (enforced in controller).
 */
@Service
public class ReturnToWorkService {

    private static final String TENANT = "default";
    private static final String MODULE = "ehs";
    private static final String ENTITY = "ReturnToWorkPlan";

    private final ReturnToWorkPlanRepository repo;
    private final EmployeeRepository employeeRepo;
    private final AccessScopeService accessScope;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public ReturnToWorkService(ReturnToWorkPlanRepository repo,
                               EmployeeRepository employeeRepo,
                               AccessScopeService accessScope,
                               AuditService audit,
                               CurrentRequest currentRequest) {
        this.repo = repo;
        this.employeeRepo = employeeRepo;
        this.accessScope = accessScope;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional
    public ReturnToWorkPlan create(UUID injuryReportId,
                                    UUID employeeId,
                                    LocalDate medicalClearanceDate,
                                    String restrictions,
                                    String modifiedSchedule) {

        ReturnToWorkPlan plan = new ReturnToWorkPlan();
        plan.setTenantId(TENANT);
        plan.setInjuryReportId(injuryReportId);
        plan.setEmployeeId(employeeId);
        plan.setMedicalClearanceDate(medicalClearanceDate);
        plan.setRestrictions(restrictions);
        plan.setModifiedSchedule(modifiedSchedule);
        plan.setStatus(ReturnToWorkStatus.DRAFT);
        plan.setManagerApproved(false);
        plan.setHrApproved(false);
        plan.setCreatedBy(currentRequest.username());
        plan.setUpdatedBy(currentRequest.username());

        ReturnToWorkPlan saved = repo.save(plan);

        audit.record(MODULE, ENTITY, saved.getId().toString(), "CREATED", null, null);

        return saved;
    }

    @Transactional
    public ReturnToWorkPlan update(UUID id,
                                    LocalDate medicalClearanceDate,
                                    String restrictions,
                                    String modifiedSchedule,
                                    ReturnToWorkStatus status) {

        ReturnToWorkPlan plan = get(id);

        ReturnToWorkStatus oldStatus = plan.getStatus();

        if (medicalClearanceDate != null) plan.setMedicalClearanceDate(medicalClearanceDate);
        if (restrictions != null) plan.setRestrictions(restrictions);
        if (modifiedSchedule != null) plan.setModifiedSchedule(modifiedSchedule);
        if (status != null) {
            plan.setStatus(status);
            if (status == ReturnToWorkStatus.COMPLETED) {
                plan.setClosedAt(OffsetDateTime.now());
            }
        }

        plan.setUpdatedBy(currentRequest.username());
        plan.setUpdatedAt(OffsetDateTime.now());

        ReturnToWorkPlan updated = repo.save(plan);

        if (status != null && !status.equals(oldStatus)) {
            audit.record(MODULE, ENTITY, id.toString(), "STATUS_CHANGE",
                    Map.of("status", oldStatus),
                    Map.of("status", status));
        }

        return updated;
    }

    @Transactional
    public void setManagerApproval(UUID id, boolean approved) {
        ReturnToWorkPlan plan = get(id);

        // Check: current user is the employee's manager
        Employee employee = employeeRepo.findById(plan.getEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        // Verify caller is the manager
        Employee currentEmployee = employeeRepo.findByUsername(currentRequest.username())
                .orElseThrow(() -> new AccessDeniedException("Current user is not an employee"));

        if (employee.getManagerId() == null || !employee.getManagerId().equals(currentEmployee.getId())) {
            throw new AccessDeniedException("Only the employee's manager can approve this RTW plan");
        }

        plan.setManagerApproved(approved);
        plan.setUpdatedBy(currentRequest.username());
        plan.setUpdatedAt(OffsetDateTime.now());

        repo.save(plan);

        audit.record(MODULE, ENTITY, id.toString(), "MANAGER_APPROVAL",
                Map.of("approved", approved), null);
    }

    @Transactional
    public void setHrApproval(UUID id, boolean approved) {
        ReturnToWorkPlan plan = get(id);

        plan.setHrApproved(approved);
        plan.setUpdatedBy(currentRequest.username());
        plan.setUpdatedAt(OffsetDateTime.now());

        repo.save(plan);

        audit.record(MODULE, ENTITY, id.toString(), "HR_APPROVAL",
                Map.of("approved", approved), null);
    }

    @Transactional(readOnly = true)
    public ReturnToWorkPlan get(UUID id) {
        ReturnToWorkPlan plan = repo.findByIdAndTenantId(id, TENANT)
                .orElseThrow(() -> new ResourceNotFoundException("Return-to-work plan not found: " + id));

        // Tenant post-check
        if (!TENANT.equals(plan.getTenantId())) {
            throw new ResourceNotFoundException("Return-to-work plan not found: " + id);
        }

        return plan;
    }

    @Transactional(readOnly = true)
    public List<ReturnToWorkPlan> list(ReturnToWorkStatus statusFilter, UUID employeeId) {
        if (statusFilter != null && employeeId != null) {
            return repo.findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(TENANT, employeeId)
                    .stream().filter(p -> p.getStatus() == statusFilter).toList();
        } else if (statusFilter != null) {
            return repo.findByTenantIdAndStatusOrderByCreatedAtDesc(TENANT, statusFilter);
        } else if (employeeId != null) {
            return repo.findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(TENANT, employeeId);
        } else {
            return repo.findByTenantIdOrderByCreatedAtDesc(TENANT);
        }
    }
}
