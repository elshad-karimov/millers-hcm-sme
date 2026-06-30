package az.millers.hcm.payroll.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.payroll.api.dto.PayrollPreFlightResponse;
import az.millers.hcm.payroll.api.dto.PayrollPreFlightResponse.EmployeeIssue;
import az.millers.hcm.payroll.api.dto.PayrollPreFlightResponse.Summary;
import az.millers.hcm.payroll.domain.PayrollRun;
import az.millers.hcm.payroll.repo.EmployeeCompensationRepository;
import az.millers.hcm.payroll.repo.PayrollRunRepository;
import az.millers.hcm.timesheet.domain.Timesheet;
import az.millers.hcm.timesheet.domain.TimesheetStatus;
import az.millers.hcm.timesheet.repo.TimesheetRepository;

@Service
public class PayrollPreFlightService {

    private final PayrollRunRepository runs;
    private final EmployeeCompensationRepository compensations;
    private final TimesheetRepository timesheets;
    private final EmployeeRepository employees;
    private final PayrollHoldService holdService;
    private final SalaryAdvanceService advanceService;

    public PayrollPreFlightService(PayrollRunRepository runs,
                                    EmployeeCompensationRepository compensations,
                                    TimesheetRepository timesheets,
                                    EmployeeRepository employees,
                                    PayrollHoldService holdService,
                                    SalaryAdvanceService advanceService) {
        this.runs = runs;
        this.compensations = compensations;
        this.timesheets = timesheets;
        this.employees = employees;
        this.holdService = holdService;
        this.advanceService = advanceService;
    }

    @Transactional(readOnly = true)
    public PayrollPreFlightResponse preFlight(UUID runId) {
        PayrollRun run = runs.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("Payroll run not found: " + runId));

        LocalDate ruleDate = YearMonth.of(run.getPeriodYear(), run.getPeriodMonth()).atDay(1);

        // Get all approved timesheets for this period
        List<Timesheet> approvedTimesheets = timesheets.findByPeriodYearAndPeriodMonthAndStatus(
                run.getPeriodYear(), run.getPeriodMonth(), TimesheetStatus.APPROVED);

        Set<UUID> employeesInScope = approvedTimesheets.stream()
                .map(Timesheet::getEmployeeId)
                .collect(Collectors.toSet());

        List<EmployeeIssue> noCompensation = new ArrayList<>();
        List<EmployeeIssue> noTimesheet = new ArrayList<>();
        List<EmployeeIssue> onHold = new ArrayList<>();
        List<EmployeeIssue> pendingAdvances = new ArrayList<>();
        List<EmployeeIssue> retroactiveSalaryChange = new ArrayList<>();

        // 1. Check for employees with no compensation
        for (UUID empId : employeesInScope) {
            boolean hasCompensation = compensations.findActiveOn(empId, ruleDate).isPresent();
            if (!hasCompensation) {
                Employee emp = employees.findById(empId).orElse(null);
                noCompensation.add(new EmployeeIssue(
                        empId,
                        emp != null ? emp.getEmployeeNo() : null,
                        emp != null ? (emp.getFirstName() + " " + emp.getLastName()) : null));
            }
        }

        // 2. Check for employees with approved timesheet but retroactive salary change
        for (Timesheet ts : approvedTimesheets) {
            compensations.findActiveOn(ts.getEmployeeId(), ruleDate)
                    .ifPresent(comp -> {
                        LocalDate periodStart = ruleDate;
                        LocalDate periodEnd = YearMonth.of(run.getPeriodYear(), run.getPeriodMonth()).atEndOfMonth();
                        // If effectiveFrom is WITHIN the payroll period, flag it
                        if (!comp.getEffectiveFrom().isBefore(periodStart) && !comp.getEffectiveFrom().isAfter(periodEnd)) {
                            Employee emp = employees.findById(ts.getEmployeeId()).orElse(null);
                            retroactiveSalaryChange.add(new EmployeeIssue(
                                    ts.getEmployeeId(),
                                    emp != null ? emp.getEmployeeNo() : null,
                                    emp != null ? (emp.getFirstName() + " " + emp.getLastName()) : null));
                        }
                    });
        }

        // 3. Check for held employees
        Set<UUID> heldIds = holdService.heldEmployeeIds(runId);
        for (UUID heldId : heldIds) {
            Employee emp = employees.findById(heldId).orElse(null);
            onHold.add(new EmployeeIssue(
                    heldId,
                    emp != null ? emp.getEmployeeNo() : null,
                    emp != null ? (emp.getFirstName() + " " + emp.getLastName()) : null));
        }

        // 4. M351: pending advances
        for (UUID empId : employeesInScope) {
            List<az.millers.hcm.payroll.domain.SalaryAdvance> advances = advanceService.findByEmployeeId(empId);
            boolean hasPending = advances.stream()
                    .anyMatch(adv -> adv.getStatus() == az.millers.hcm.payroll.domain.SalaryAdvanceStatus.PENDING);
            if (hasPending) {
                Employee emp = employees.findById(empId).orElse(null);
                pendingAdvances.add(new EmployeeIssue(
                        empId,
                        emp != null ? emp.getEmployeeNo() : null,
                        emp != null ? (emp.getFirstName() + " " + emp.getLastName()) : null));
            }
        }

        int totalIssues = noCompensation.size() + retroactiveSalaryChange.size() +
                onHold.size() + pendingAdvances.size();

        Summary summary = new Summary(totalIssues, employeesInScope.size());

        return new PayrollPreFlightResponse(
                summary,
                noCompensation,
                noTimesheet,
                onHold,
                pendingAdvances,
                retroactiveSalaryChange);
    }
}
