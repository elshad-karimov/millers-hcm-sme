package az.millers.hcm.timesheet.service;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.domain.EmploymentStatus;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.timesheet.domain.Timesheet;
import az.millers.hcm.timesheet.domain.TimesheetStatus;
import az.millers.hcm.timesheet.repo.TimesheetRepository;

/**
 * Monthly scheduler that auto-generates and auto-submits timesheets
 * for the previous calendar month (M189 / PRD §9.4 timesheet lifecycle).
 *
 * <p>Runs on the 1st of each month:
 * <ol>
 *   <li><b>00:30 UTC</b> — generates a DRAFT timesheet for every ACTIVE employee
 *       who does not yet have one for the previous month.</li>
 *   <li><b>01:00 UTC</b> — auto-submits all DRAFT / REOPENED timesheets for the
 *       previous month.  Employees who already submitted manually are unaffected.</li>
 * </ol>
 *
 * <p>Both steps run in a scheduler thread where {@code CurrentRequest.username()}
 * returns {@code "system"} and {@code AccessScopeService.isAccessible()} is
 * unrestricted — no auth-context plumbing needed.
 *
 * <p>Failures are logged per-employee and never abort the batch.
 */
@Component
public class TimesheetAutoScheduler {

    private static final Logger log = LoggerFactory.getLogger(TimesheetAutoScheduler.class);

    private final TimesheetService timesheetService;
    private final TimesheetRepository timesheets;
    private final EmployeeRepository employees;

    public TimesheetAutoScheduler(TimesheetService timesheetService,
                                   TimesheetRepository timesheets,
                                   EmployeeRepository employees) {
        this.timesheetService = timesheetService;
        this.timesheets = timesheets;
        this.employees = employees;
    }

    /**
     * Step 1 — auto-generate.
     * Runs at 00:30 UTC on the 1st of each month.
     */
    @Scheduled(cron = "0 30 0 1 * *")
    public void autoGenerate() {
        YearMonth prev = YearMonth.now(ZoneOffset.UTC).minusMonths(1);
        int year  = prev.getYear();
        int month = prev.getMonthValue();
        log.info("TimesheetAutoScheduler.autoGenerate: generating timesheets for {}-{}", year, month);

        // Employee IDs that already have a timesheet (any status) for the period.
        Set<UUID> alreadyExists = timesheets
                .findByPeriodYearAndPeriodMonthOrderByEmployeeIdAsc(year, month)
                .stream()
                .map(Timesheet::getEmployeeId)
                .collect(Collectors.toSet());

        List<Employee> active = employees.findAllByEmploymentStatus(EmploymentStatus.ACTIVE);
        int generated = 0;
        for (Employee emp : active) {
            if (alreadyExists.contains(emp.getId())) continue;
            try {
                timesheetService.generate(emp.getId(), year, month);
                generated++;
            } catch (Exception ex) {
                log.warn("TimesheetAutoScheduler.autoGenerate: failed for employee {} {}-{}: {}",
                        emp.getId(), year, month, ex.getMessage());
            }
        }
        log.info("TimesheetAutoScheduler.autoGenerate: generated {} timesheets for {}-{}", generated, year, month);
    }

    /**
     * Step 2 — auto-submit.
     * Runs at 01:00 UTC on the 1st of each month (30 min after auto-generate).
     * Submits all DRAFT and REOPENED timesheets for the previous month.
     */
    @Scheduled(cron = "0 0 1 1 * *")
    public void autoSubmit() {
        YearMonth prev = YearMonth.now(ZoneOffset.UTC).minusMonths(1);
        int year  = prev.getYear();
        int month = prev.getMonthValue();
        log.info("TimesheetAutoScheduler.autoSubmit: submitting overdue DRAFT timesheets for {}-{}", year, month);

        List<Timesheet> pending = timesheets.findByPeriodYearAndPeriodMonthAndStatusIn(
                year, month, List.of(TimesheetStatus.DRAFT, TimesheetStatus.REOPENED));

        int submitted = 0;
        for (Timesheet ts : pending) {
            try {
                timesheetService.submit(ts.getId());
                submitted++;
            } catch (Exception ex) {
                log.warn("TimesheetAutoScheduler.autoSubmit: failed for timesheet {} employee {} {}-{}: {}",
                        ts.getId(), ts.getEmployeeId(), year, month, ex.getMessage());
            }
        }
        log.info("TimesheetAutoScheduler.autoSubmit: auto-submitted {} timesheets for {}-{}", submitted, year, month);
    }
}
