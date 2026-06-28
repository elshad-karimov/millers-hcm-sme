package az.millers.hcm.attendance.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.attendance.domain.AttendancePolicy;
import az.millers.hcm.attendance.repo.AttendancePolicyRepository;
import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.organization.repo.LegalEntityRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * Attendance Policy CRUD + employee-resolution service (PRD §2, §25, §26).
 *
 * <p>Resolution order: (dept + type) > dept-only > type-only > catch-all.
 * The AttendanceEngine calls {@link #findForEmployee} to get the rule set
 * that applies to a specific employee before computing deductions.
 */
@Service
public class AttendancePolicyService {

    private static final String MODULE = "ATTENDANCE";

    private final AttendancePolicyRepository repo;
    private final EmployeeRepository employees;
    private final LegalEntityRepository legalEntities;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public AttendancePolicyService(AttendancePolicyRepository repo,
                                   EmployeeRepository employees,
                                   LegalEntityRepository legalEntities,
                                   AuditService audit,
                                   CurrentRequest currentRequest) {
        this.repo = repo;
        this.employees = employees;
        this.legalEntities = legalEntities;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    // ── Queries ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AttendancePolicy> list() {
        return repo.findByTenantIdAndActiveTrueOrderByNameAsc(defaultTenantId());
    }

    @Transactional(readOnly = true)
    public AttendancePolicy get(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance policy not found: " + id));
    }

    /**
     * Find the best-matching policy for an employee.
     * Falls back to an empty Optional when no catch-all exists yet —
     * callers should use engine defaults in that case.
     */
    @Transactional(readOnly = true)
    public Optional<AttendancePolicy> findForEmployee(UUID employeeId) {
        Employee emp = employees.findById(employeeId).orElse(null);
        if (emp == null) return Optional.empty();
        String empType = emp.getEmploymentType() != null ? emp.getEmploymentType().name() : null;
        return repo.findBestMatch(defaultTenantId(), emp.getOrgUnitId(), empType);
    }

    // ── Mutations ─────────────────────────────────────────────────────────

    @Transactional
    public AttendancePolicy create(AttendancePolicy policy) {
        UUID tenantId = defaultTenantId();
        if (repo.findByTenantIdAndCode(tenantId, policy.getCode()).isPresent()) {
            throw new BadRequestException("Policy code already exists: " + policy.getCode());
        }
        policy.setTenantId(tenantId);
        policy.setCreatedBy(currentRequest.username());
        policy.setUpdatedBy(currentRequest.username());
        AttendancePolicy saved = repo.save(policy);
        audit.record(MODULE, "AttendancePolicy", saved.getId().toString(), "CREATED", null, saved);
        return saved;
    }

    @Transactional
    public AttendancePolicy update(UUID id, AttendancePolicy patch) {
        AttendancePolicy p = get(id);
        if (!p.getCode().equals(patch.getCode())
                && repo.existsByTenantIdAndCodeAndIdNot(p.getTenantId(), patch.getCode(), id)) {
            throw new BadRequestException("Policy code already exists: " + patch.getCode());
        }
        AttendancePolicy before = snapshot(p);

        p.setCode(patch.getCode());
        p.setName(patch.getName());
        p.setDescription(patch.getDescription());
        p.setDepartmentId(patch.getDepartmentId());
        p.setLocationId(patch.getLocationId());
        p.setEmploymentType(patch.getEmploymentType());
        p.setClockInGraceMinutes(patch.getClockInGraceMinutes());
        p.setClockOutGraceMinutes(patch.getClockOutGraceMinutes());
        p.setLateDeductionEnabled(patch.isLateDeductionEnabled());
        p.setLateMaxBeforeHalfDayMinutes(patch.getLateMaxBeforeHalfDayMinutes());
        p.setLateMonthlyThresholdMinutes(patch.getLateMonthlyThresholdMinutes());
        p.setEarlyLeaveDeductionEnabled(patch.isEarlyLeaveDeductionEnabled());
        p.setEarlyLeaveTreatment(patch.getEarlyLeaveTreatment());
        p.setEarlyLeaveToleranceMinutes(patch.getEarlyLeaveToleranceMinutes());
        p.setAbsenceDeductionEnabled(patch.isAbsenceDeductionEnabled());
        p.setAbsenceDailyDivisor(patch.getAbsenceDailyDivisor());
        p.setHoursPerDay(patch.getHoursPerDay());
        p.setUnauthorizedAbsenceTreatment(patch.getUnauthorizedAbsenceTreatment());
        p.setOvertimeRequiresPreapproval(patch.isOvertimeRequiresPreapproval());
        p.setOvertimeDeptHeadThresholdMinutes(patch.getOvertimeDeptHeadThresholdMinutes());
        p.setCompOffEnabled(patch.isCompOffEnabled());
        p.setAutoBreakDeductionEnabled(patch.isAutoBreakDeductionEnabled());
        p.setBreakMinutes(patch.getBreakMinutes());
        p.setMinHoursForBreakDeduction(patch.getMinHoursForBreakDeduction());
        p.setRoundingEnabled(patch.isRoundingEnabled());
        p.setRoundingMinutes(patch.getRoundingMinutes());
        p.setRoundingDirection(patch.getRoundingDirection());
        p.setActive(patch.isActive());
        p.setUpdatedBy(currentRequest.username());

        AttendancePolicy saved = repo.save(p);
        audit.record(MODULE, "AttendancePolicy", id.toString(), "UPDATED", before, saved);
        return saved;
    }

    @Transactional
    public void setActive(UUID id, boolean active) {
        AttendancePolicy p = get(id);
        p.setActive(active);
        p.setUpdatedBy(currentRequest.username());
        repo.save(p);
        audit.record(MODULE, "AttendancePolicy", id.toString(), active ? "ACTIVATED" : "DEACTIVATED", null, null);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    public UUID defaultTenantId() {
        return legalEntities.findAllByOrderByCodeAsc().stream()
                .findFirst()
                .map(e -> e.getId())
                .orElseThrow(() -> new IllegalStateException("No legal entity found — seed required"));
    }

    private AttendancePolicy snapshot(AttendancePolicy p) {
        AttendancePolicy s = new AttendancePolicy();
        s.setId(p.getId());
        s.setCode(p.getCode());
        s.setName(p.getName());
        return s;
    }
}
