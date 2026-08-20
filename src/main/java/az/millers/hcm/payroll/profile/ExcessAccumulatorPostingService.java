package az.millers.hcm.payroll.profile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.payroll.timepay.PeriodNormHours;
import az.millers.hcm.payroll.timepay.PeriodNormHoursRepository;
import az.millers.hcm.timesheet.domain.Timesheet;
import az.millers.hcm.timesheet.domain.TimesheetMonthTotal;
import az.millers.hcm.timesheet.domain.TimesheetStatus;
import az.millers.hcm.timesheet.event.TimesheetPeriodLockedEvent;
import az.millers.hcm.timesheet.repo.TimesheetMonthTotalRepository;
import az.millers.hcm.timesheet.repo.TimesheetRepository;

/**
 * Posts a locked month into the balancing accumulator of every employee on a
 * summarised working-time contract.
 *
 * <p>This is what turns the ledger from a data model into a working one. It
 * listens for the attendance period lock rather than being called by the
 * timesheet module, so timesheet stays unaware of payroll, and it runs
 * <strong>after commit</strong> so a failure here can never roll back a lock
 * that HR has already been told succeeded.
 *
 * <h2>It records; it does not pay</h2>
 * Posting a month only says how far above or below norm the employee was. The
 * hours become money at settlement, in April, August or December — and
 * settlement still refuses until the rotation excess multiplier is answered
 * (BLOCKERS Q2). An employee can therefore accumulate all year with no risk of
 * a wrong payment.
 *
 * <h2>Failure is loud but contained</h2>
 * One employee's missing norm hours or unconfigured categories must not stop
 * the other forty from being posted. Each failure is collected, audited and
 * returned; the rest of the period still posts.
 */
@Service
public class ExcessAccumulatorPostingService {

    private static final Logger log =
            LoggerFactory.getLogger(ExcessAccumulatorPostingService.class);

    private static final String MODULE = "PAYROLL";
    private static final String ENTITY = "ExcessAccumulator";

    private final TimesheetRepository timesheets;
    private final TimesheetMonthTotalRepository monthTotals;
    private final PeriodNormHoursRepository normHours;
    private final EmployeeCalculationProfileRepository assignments;
    private final CalculationProfileRepository profiles;
    private final ExcessAccumulatorService accumulator;
    private final AuditService audit;

    public ExcessAccumulatorPostingService(TimesheetRepository timesheets,
                                           TimesheetMonthTotalRepository monthTotals,
                                           PeriodNormHoursRepository normHours,
                                           EmployeeCalculationProfileRepository assignments,
                                           CalculationProfileRepository profiles,
                                           ExcessAccumulatorService accumulator,
                                           AuditService audit) {
        this.timesheets = timesheets;
        this.monthTotals = monthTotals;
        this.normHours = normHours;
        this.assignments = assignments;
        this.profiles = profiles;
        this.accumulator = accumulator;
        this.audit = audit;
    }

    /** What one posting run did, so it can be shown rather than guessed at. */
    public record PostingResult(
            int year,
            int month,
            int posted,
            int skipped,
            List<PostedRow> rows,
            /** employeeId -> why nothing was posted for them */
            Map<String, String> problems) {
    }

    public record PostedRow(
            UUID employeeId,
            BigDecimal actualHours,
            BigDecimal normHours,
            BigDecimal deltaHours,
            BigDecimal runningBalance,
            String categoriesUsed) {
    }

    /**
     * Post automatically when the attendance period locks.
     *
     * <p>{@code AFTER_COMMIT} deliberately: the lock is already durable, and a
     * problem in payroll configuration is a payroll problem to fix, not a reason
     * to reopen an attendance period.
     */
    @TransactionalEventListener
    public void onPeriodLocked(TimesheetPeriodLockedEvent event) {
        try {
            PostingResult result = post(event.year(), event.month(), "PERIOD_LOCK");
            log.info("Excess accumulator: posted {} employee-months for {}-{} on period lock, "
                            + "{} skipped", result.posted(), event.year(), event.month(),
                    result.skipped());
        } catch (RuntimeException e) {
            // Never propagate: the period is locked and committed. Surfacing
            // this as a failed lock would be a lie about what happened.
            log.error("Excess accumulator posting failed for {}-{} after period lock. "
                            + "The lock stands; re-post from the payroll admin endpoint once "
                            + "the cause is fixed.", event.year(), event.month(), e);
        }
    }

    /**
     * Post (or re-post) a period for every employee on a balancing profile.
     *
     * <p>Re-posting is safe and is the correct response to a corrected
     * timesheet: {@link ExcessAccumulatorService#recordMonth} replaces the
     * month and recomputes every later running balance. A period that has
     * already been settled refuses, because a settled payment is never
     * rewritten.
     */
    @Transactional
    public PostingResult post(int year, int month, String source) {
        List<Timesheet> locked = timesheets
                .findByPeriodYearAndPeriodMonthOrderByEmployeeIdAsc(year, month).stream()
                .filter(t -> t.getStatus() == TimesheetStatus.LOCKED
                        || t.getStatus() == TimesheetStatus.APPROVED)
                .toList();

        BigDecimal norm = normHours.findByPeriodYearAndPeriodMonth(year, month)
                .map(PeriodNormHours::getNormHours)
                .orElse(null);

        List<PostedRow> rows = new ArrayList<>();
        Map<String, String> problems = new LinkedHashMap<>();

        for (Timesheet ts : locked) {
            UUID employeeId = ts.getEmployeeId();
            try {
                CalculationProfile profile = profileFor(employeeId, YearMonth.of(year, month));
                if (profile == null || !profile.settlesExcessOverBalancingPeriod()) {
                    continue; // not a summarised-accounting employee; nothing to accumulate
                }
                if (norm == null) {
                    throw new BadRequestException("Norm working hours are not configured for "
                            + year + "-" + String.format("%02d", month)
                            + ", so actual hours cannot be compared against anything.");
                }
                List<String> categories = profile.accumulatorCategoryCodes();
                if (categories.isEmpty()) {
                    throw new BadRequestException("Profile " + profile.getCode()
                            + " has no accumulator categories configured, so there is no "
                            + "defensible way to total this month's eligible hours "
                            + "(BLOCKERS Q6.1).");
                }

                BigDecimal actual = eligibleHours(ts.getId(), categories);
                ExcessAccumulator acc = accumulator.recordMonth(
                        employeeId, profile.getBalancingSchemeCode(), year, month,
                        actual, norm, source, String.join(",", categories));

                rows.add(new PostedRow(employeeId, actual, norm, actual.subtract(norm),
                        acc.getBalanceHours(), String.join(",", categories)));

            } catch (RuntimeException e) {
                // One employee's configuration gap must not hide the rest.
                problems.put(employeeId.toString(), e.getMessage());
            }
        }

        audit.record(MODULE, ENTITY, year + "-" + month, "ACCUMULATOR_POST", null,
                Map.of("source", source, "posted", rows.size(), "skipped", problems.size()));

        return new PostingResult(year, month, rows.size(), problems.size(), rows, problems);
    }

    // ---------- Internals ----------

    private BigDecimal eligibleHours(UUID timesheetId, List<String> categories) {
        Map<String, BigDecimal> totals = monthTotals
                .findByTimesheetIdOrderByCategoryCodeAsc(timesheetId).stream()
                .collect(Collectors.toMap(TimesheetMonthTotal::getCategoryCode,
                        TimesheetMonthTotal::getQuantity, BigDecimal::add, LinkedHashMap::new));

        BigDecimal sum = BigDecimal.ZERO;
        for (String code : categories) {
            BigDecimal q = totals.get(code);
            if (q != null) sum = sum.add(q);
        }
        return sum;
    }

    private CalculationProfile profileFor(UUID employeeId, YearMonth period) {
        LocalDate start = period.atDay(1);
        return assignments.findByEmployeeIdOrderByEffectiveFromDesc(employeeId).stream()
                .filter(a -> a.coversPeriodStart(start))
                .findFirst()
                .map(EmployeeCalculationProfile::getProfileCode)
                .flatMap(profiles::findByCode)
                .orElse(null);
    }
}
