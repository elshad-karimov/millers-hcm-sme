package az.millers.hcm.attendance.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.attendance.domain.AttendancePayrollSummary;
import az.millers.hcm.attendance.repo.AttendancePayrollSummaryRepository;
import az.millers.hcm.attendance.service.AttendanceDeductionBridge.AttendanceDeductionResult;
import az.millers.hcm.organization.repo.LegalEntityRepository;
import az.millers.hcm.payroll.repo.PayrollResultRepository;
import az.millers.hcm.payroll.repo.PayrollRunRepository;

/**
 * M331: Builds per-employee attendance impact rows for a payroll run.
 *
 * <p>Called from {@link az.millers.hcm.payroll.service.PayrollRunService#calculate(UUID)}
 * immediately after {@link az.millers.hcm.payroll.service.PayrollEngine#calculate} returns.
 * Gives the payroll approver a pre-approval breakdown: who was late, how many
 * absent days, and what salary deduction each attendance issue produced.
 *
 * <p>Idempotent: existing rows for the run are deleted then rebuilt so recalc
 * always reflects the latest daily summaries.
 */
@Service
public class AttendancePayrollSummaryService {

    private final AttendanceDeductionBridge bridge;
    private final AttendancePayrollSummaryRepository repo;
    private final PayrollRunRepository payrollRuns;
    private final PayrollResultRepository payrollResults;
    private final LegalEntityRepository legalEntities;

    public AttendancePayrollSummaryService(AttendanceDeductionBridge bridge,
                                            AttendancePayrollSummaryRepository repo,
                                            PayrollRunRepository payrollRuns,
                                            PayrollResultRepository payrollResults,
                                            LegalEntityRepository legalEntities) {
        this.bridge = bridge;
        this.repo = repo;
        this.payrollRuns = payrollRuns;
        this.payrollResults = payrollResults;
        this.legalEntities = legalEntities;
    }

    @Transactional
    public List<AttendancePayrollSummary> compute(UUID runId) {
        var run = payrollRuns.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Payroll run not found: " + runId));

        UUID tenantId = defaultTenantId();

        // Wipe previous rows so re-calculate is always fresh
        repo.deleteByPayrollRunId(runId);
        repo.flush();

        var payResults = payrollResults.findByRunIdOrderByEmployeeIdAsc(runId);
        List<AttendancePayrollSummary> built = new ArrayList<>();

        for (var pr : payResults) {
            AttendanceDeductionResult ded = bridge.compute(
                    pr.getEmployeeId(), run.getPeriodYear(), run.getPeriodMonth(),
                    pr.getBaseSalary());

            AttendancePayrollSummary summary = new AttendancePayrollSummary();
            summary.setTenantId(tenantId);
            summary.setPayrollRunId(runId);
            summary.setEmployeeId(pr.getEmployeeId());
            summary.setPeriodYear(run.getPeriodYear());
            summary.setPeriodMonth(run.getPeriodMonth());
            summary.setTotalLateMinutes(ded.totalLateMinutes());
            summary.setTotalEarlyMinutes(ded.totalEarlyMinutes());
            summary.setTotalAbsentDays(ded.totalAbsentDays());
            summary.setTotalOvertimeMinutes(ded.totalOvertimeMinutes());
            summary.setLateDeductionAmount(ded.lateDeduction());
            summary.setEarlyLeaveDeduction(ded.earlyLeaveDeduction());
            summary.setAbsenceDeductionAmount(ded.absenceDeduction());
            summary.setTotalDeductionAmount(ded.totalDeduction());
            summary.setPolicyApplied(ded.policyApplied());
            summary.setPolicyCode(ded.policyCode());
            summary.setComputedAt(OffsetDateTime.now());
            built.add(repo.save(summary));
        }

        return built;
    }

    @Transactional(readOnly = true)
    public List<AttendancePayrollSummary> getByRun(UUID runId) {
        return repo.findByPayrollRunIdOrderByEmployeeIdAsc(runId);
    }

    private UUID defaultTenantId() {
        return legalEntities.findAllByOrderByCodeAsc().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No legal entity found"))
                .getId();
    }
}
