package az.millers.hcm.corehr.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.common.history.EffectiveDatedRecord;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.domain.EmployeeEmploymentHistory;
import az.millers.hcm.corehr.domain.EmployeeStatusHistory;
import az.millers.hcm.corehr.domain.EmploymentStatus;
import az.millers.hcm.corehr.domain.EmploymentType;
import az.millers.hcm.corehr.repo.EmployeeEmploymentHistoryRepository;
import az.millers.hcm.corehr.repo.EmployeeStatusHistoryRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * Single source of truth for writing into the two effective-dated employee
 * history tables (M62 / P1-10 + P1-11).
 *
 * <p>Every other service in the codebase that wants to record a transition
 * (EmployeeService on create / changeStatus, ContractChangeService on apply,
 * TerminationService on processing) routes through this service — so the
 * close-prior-row / save-new-row choreography lives in exactly one place,
 * using {@link EffectiveDatedRecord#closeOn(LocalDate)}.
 *
 * <p>Read methods are exposed here too so the upcoming
 * {@code /api/employees/{id}/history} endpoint (M70) and the lifecycle history
 * tab have a single read path.
 */
@Service
public class EmployeeHistoryService {

    private final EmployeeEmploymentHistoryRepository employmentHistory;
    private final EmployeeStatusHistoryRepository statusHistory;
    private final CurrentRequest currentRequest;

    public EmployeeHistoryService(EmployeeEmploymentHistoryRepository employmentHistory,
                                   EmployeeStatusHistoryRepository statusHistory,
                                   CurrentRequest currentRequest) {
        this.employmentHistory = employmentHistory;
        this.statusHistory = statusHistory;
        this.currentRequest = currentRequest;
    }

    // ── Reads ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<EmployeeEmploymentHistory> employmentHistoryFor(UUID employeeId) {
        return employmentHistory.findByEmployeeIdOrderByEffectiveFromDesc(employeeId);
    }

    @Transactional(readOnly = true)
    public List<EmployeeStatusHistory> statusHistoryFor(UUID employeeId) {
        return statusHistory.findByEmployeeIdOrderByEffectiveFromDesc(employeeId);
    }

    // ── Writes ────────────────────────────────────────────────────────────────

    /**
     * Records a new <strong>employment</strong> slice. If a slice is currently
     * open ({@code effective_to IS NULL}) and starts on or before the new
     * {@code effectiveFrom}, it is closed via
     * {@link EffectiveDatedRecord#closeOn(LocalDate)}.
     *
     * <p>Use this whenever any of (position / department / manager / grade /
     * employment_type / fte_percent / cost_centre) changes.
     *
     * @param employee       the employee in its post-transition state
     *                       (caller has already mutated and saved the Employee)
     * @param effectiveFrom  the day the new slice begins
     * @param changeReason   free-text label for the transition
     * @param sourceModule   originating subsystem (e.g. "LIFECYCLE")
     * @param sourceEntity   originating entity type (e.g. "ContractChange")
     * @param sourceId       originating record id, stringified (nullable)
     */
    @Transactional
    public EmployeeEmploymentHistory recordEmploymentSlice(Employee employee,
                                                            LocalDate effectiveFrom,
                                                            String changeReason,
                                                            String sourceModule,
                                                            String sourceEntity,
                                                            String sourceId) {
        if (employee == null) throw new IllegalArgumentException("employee is required");
        if (effectiveFrom == null) throw new IllegalArgumentException("effectiveFrom is required");

        closePriorOpenSlice(
                employmentHistory.findOpenForEmployee(employee.getId()),
                effectiveFrom,
                employmentHistory::save);

        EmployeeEmploymentHistory slice = new EmployeeEmploymentHistory();
        slice.setEmployeeId(employee.getId());
        slice.setEffectiveFrom(effectiveFrom);
        slice.setEffectiveTo(null); // open-ended
        slice.setPositionId(employee.getPositionId());
        slice.setPositionTitle(employee.getPositionTitle());
        slice.setOrgUnitId(employee.getOrgUnitId());
        slice.setDepartmentName(employee.getDepartmentName());
        slice.setCostCentre(employee.getCostCentre());
        slice.setManagerId(employee.getManagerId());
        slice.setEmploymentType(employee.getEmploymentType() != null
                ? employee.getEmploymentType() : EmploymentType.PERMANENT);
        slice.setFtePercent(employee.getFtePercent() != null
                ? employee.getFtePercent() : new BigDecimal("100.00"));
        slice.setChangeReason(changeReason);
        slice.setSourceModule(sourceModule);
        slice.setSourceEntity(sourceEntity);
        slice.setSourceId(sourceId);
        slice.setChangedBy(currentRequest.username());
        return employmentHistory.save(slice);
    }

    /**
     * Records a new <strong>status</strong> slice. The currently-open status
     * slice (if any) is closed via {@link EffectiveDatedRecord#closeOn(LocalDate)}.
     */
    @Transactional
    public EmployeeStatusHistory recordStatusSlice(UUID employeeId,
                                                    EmploymentStatus status,
                                                    LocalDate effectiveFrom,
                                                    String reason,
                                                    String sourceModule,
                                                    String sourceEntity,
                                                    String sourceId) {
        if (employeeId == null) throw new IllegalArgumentException("employeeId is required");
        if (status == null) throw new IllegalArgumentException("status is required");
        if (effectiveFrom == null) throw new IllegalArgumentException("effectiveFrom is required");

        closePriorOpenSlice(
                statusHistory.findOpenForEmployee(employeeId),
                effectiveFrom,
                statusHistory::save);

        EmployeeStatusHistory slice = new EmployeeStatusHistory();
        slice.setEmployeeId(employeeId);
        slice.setStatus(status);
        slice.setEffectiveFrom(effectiveFrom);
        slice.setEffectiveTo(null);
        slice.setReason(reason);
        slice.setSourceModule(sourceModule);
        slice.setSourceEntity(sourceEntity);
        slice.setSourceId(sourceId);
        slice.setChangedBy(currentRequest.username());
        return statusHistory.save(slice);
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    /**
     * The DRY centrepiece. Given an {@link java.util.Optional} of an
     * {@link EffectiveDatedRecord} that may currently be open, close it via
     * the shared default method and persist via the caller's repository save.
     *
     * <p>Same-day re-transitions (a new slice on the same {@code effective_from}
     * as the open slice) are tolerated: we delete the stale open row instead of
     * closing it, since
     * {@link EffectiveDatedRecord#closeOn(LocalDate)} would otherwise throw
     * "not strictly after". This matches HR behaviour where two changes on the
     * same day should fold into a single slice.
     */
    private <T extends EffectiveDatedRecord> void closePriorOpenSlice(
            java.util.Optional<T> openSlice,
            LocalDate newEffectiveFrom,
            java.util.function.Consumer<T> persist) {
        openSlice.ifPresent(row -> {
            if (row.getEffectiveFrom() != null
                    && row.getEffectiveFrom().equals(newEffectiveFrom)) {
                // Same-day re-transition — the new slice supersedes the open row.
                // We cannot close it cleanly (effective_to would equal
                // effective_from - 1, violating the chk_*_date_order constraint),
                // so the cleaner option is to overwrite by deleting the prior
                // open row. Audit history of the deleted snapshot lives in
                // audit_log via the caller's audit.record() invocation.
                if (row instanceof EmployeeEmploymentHistory eh) {
                    employmentHistory.delete(eh);
                } else if (row instanceof EmployeeStatusHistory sh) {
                    statusHistory.delete(sh);
                }
                return;
            }
            row.closeOn(newEffectiveFrom);
            persist.accept(row);
        });
    }
}
