package az.millers.hcm.payroll.profile;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;

/**
 * The excess-hour ledger for employees on summarised working-time accounting.
 *
 * <p>Rotation staff do not have their overtime decided monthly. Hours accumulate
 * against the norm across a fixed balancing period — Jan–Apr, May–Aug, Sep–Dec —
 * and only the closing difference is payable, in April, August and December.
 * Months 1–3 pay nothing.
 *
 * <p>The running balance may go negative mid-period; a shortfall in one month is
 * genuinely offset by a surplus in another, which is the whole point of the
 * method. Only the closing figure is floored at zero: a below-norm period pays
 * nothing and creates no debt.
 *
 * <p>Nothing here pays anyone. It records what is owed and hands the hours to
 * the calculator, which refuses to price them until the rotation excess
 * multiplier is configured (BLOCKERS Q2).
 */
@Service
public class ExcessAccumulatorService {

    private static final String MODULE = "PAYROLL";
    private static final String ENTITY = "ExcessAccumulator";

    private final ExcessAccumulatorRepository accumulators;
    private final ExcessAccumulatorMonthRepository months;
    private final BalancingPeriodDefRepository periodDefs;
    /** Optional so the arithmetic stays unit-testable without a Spring context. */
    private final AuditService audit;

    /** Test-only: the arithmetic without an audit sink or a Spring context. */
    ExcessAccumulatorService(ExcessAccumulatorRepository accumulators,
                             ExcessAccumulatorMonthRepository months,
                             BalancingPeriodDefRepository periodDefs) {
        this(accumulators, months, periodDefs, null);
    }

    @Autowired
    public ExcessAccumulatorService(ExcessAccumulatorRepository accumulators,
                                    ExcessAccumulatorMonthRepository months,
                                    BalancingPeriodDefRepository periodDefs,
                                    AuditService audit) {
        this.accumulators = accumulators;
        this.months = months;
        this.periodDefs = periodDefs;
        this.audit = audit;
    }

    /** One period's ledger, month by month, as it will be read at settlement. */
    public record Ledger(ExcessAccumulator accumulator, List<ExcessAccumulatorMonth> months) {
    }

    /**
     * Record one month's actual against norm and return the updated period.
     *
     * <p>Re-recording a month that has already been recorded replaces it and
     * recomputes every later month's running balance — a corrected timesheet
     * must not leave a stale balance behind. Recording into a settled period is
     * refused: a settled payment is never rewritten (global rules 12–14), the
     * correction belongs in the next open period.
     */
    @Transactional
    public ExcessAccumulator recordMonth(UUID employeeId, String schemeCode,
                                         int year, int month,
                                         BigDecimal actualHours, BigDecimal normHours,
                                         String recordedBy) {
        return recordMonth(employeeId, schemeCode, year, month, actualHours, normHours,
                recordedBy, null);
    }

    /** As above, recording where the figures came from and what they were summed from. */
    @Transactional
    public ExcessAccumulator recordMonth(UUID employeeId, String schemeCode,
                                         int year, int month,
                                         BigDecimal actualHours, BigDecimal normHours,
                                         String source, String categoriesUsed) {
        BalancingPeriodDef def = periodFor(schemeCode, month);
        ExcessAccumulator acc = openOrCreate(employeeId, schemeCode, def, year);

        if (acc.isSettled()) {
            throw new BadRequestException(
                    "The " + year + " period " + def.getPeriodSeq() + " balance is already "
                            + "settled. A correction to " + year + "-" + month + " has to be "
                            + "made as an adjustment in the next open period — a settled "
                            + "payment is never rewritten.");
        }

        Optional<ExcessAccumulatorMonth> existing =
                months.findByAccumulatorIdAndPeriodYearAndPeriodMonth(acc.getId(), year, month);

        ExcessAccumulatorMonth row = existing.orElseGet(ExcessAccumulatorMonth::new);
        row.setAccumulatorId(acc.getId());
        row.setEmployeeId(employeeId);
        row.setPeriodYear(year);
        row.setPeriodMonth(month);
        row.setActualHours(actualHours);
        row.setNormHours(normHours);
        row.setDeltaHours(actualHours.subtract(normHours));
        row.setRunningBalance(BigDecimal.ZERO); // recomputed below
        row.setRecordedBy(source);
        row.setSource(normaliseSource(source));
        if (categoriesUsed != null) row.setCategoriesUsed(categoriesUsed);
        months.save(row);

        return recomputeBalances(acc);
    }

    /**
     * The hours a settlement releases in this payroll month, or empty when the
     * month is not a settlement month for this scheme.
     *
     * <p>Read-only: it says what is due without closing anything, so a preview
     * can show the settlement before anyone commits to paying it.
     */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> dueThisMonth(UUID employeeId, String schemeCode,
                                             int year, int month) {
        BalancingPeriodDef def = periodFor(schemeCode, month);
        if (!def.settlesIn(month)) return Optional.empty();

        return accumulators
                .findByEmployeeIdAndPeriodYearAndPeriodSeq(employeeId, year, def.getPeriodSeq())
                .map(ExcessAccumulator::payableHours);
    }

    /**
     * Close a period and record how it was settled.
     *
     * <p>Writes the hours, the multiplier and the amount that were actually
     * used, so the payment can be read back exactly as it was decided even if a
     * norm or a rate is later changed.
     */
    @Transactional
    public ExcessAccumulator settle(UUID employeeId, int year, int periodSeq,
                                    BigDecimal multiplier, BigDecimal hourlyRate,
                                    String settledBy) {
        return settle(employeeId, year, periodSeq, multiplier, hourlyRate, settledBy,
                null, null, null);
    }

    /** As above, recording which payroll period carried the settlement. */
    @Transactional
    public ExcessAccumulator settle(UUID employeeId, int year, int periodSeq,
                                    BigDecimal multiplier, BigDecimal hourlyRate,
                                    String settledBy,
                                    Integer paidInYear, Integer paidInMonth, String note) {
        ExcessAccumulator acc = accumulators
                .findByEmployeeIdAndPeriodYearAndPeriodSeq(employeeId, year, periodSeq)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No excess accumulator for that employee and period."));

        if (acc.isSettled()) {
            throw new BadRequestException("That period was already settled on "
                    + acc.getSettledAt() + ". Settling twice would pay the same hours twice.");
        }
        if (multiplier == null) {
            throw new BadRequestException(
                    "The rotation excess multiplier is not configured. It is either 3.50 "
                            + "(2 x 1.75) or 2.75 (2 + 0.75) and the source material does not "
                            + "say which — BLOCKERS Q2. Settling without it would guess at "
                            + "someone's pay.");
        }

        BigDecimal hours = acc.payableHours();
        acc.setSettledExcessHours(hours);
        acc.setSettledMultiplier(multiplier);
        acc.setSettledAmount(hours.multiply(hourlyRate).multiply(multiplier)
                .setScale(2, java.math.RoundingMode.HALF_UP));
        acc.setSettledAt(OffsetDateTime.now());
        acc.setSettledBy(settledBy);
        acc.setSettledInPeriodYear(paidInYear);
        acc.setSettledInPeriodMonth(paidInMonth);
        acc.setSettlementNote(note);
        acc.setStatus(ExcessAccumulator.SETTLED);
        ExcessAccumulator saved = accumulators.save(acc);

        // Every payroll settlement is traceable (global rules 10-11).
        if (audit != null) {
            audit.record(MODULE, ENTITY, saved.getId().toString(), "EXCESS_SETTLE", null,
                    java.util.Map.of(
                            "employeeId", employeeId.toString(),
                            "periodYear", year,
                            "periodSeq", periodSeq,
                            "hours", hours.toPlainString(),
                            "multiplier", multiplier.toPlainString(),
                            "amount", saved.getSettledAmount().toPlainString()));
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Ledger> ledgersFor(UUID employeeId) {
        List<Ledger> out = new ArrayList<>();
        for (ExcessAccumulator acc :
                accumulators.findByEmployeeIdOrderByPeriodYearDescPeriodSeqDesc(employeeId)) {
            out.add(new Ledger(acc,
                    months.findByAccumulatorIdOrderByPeriodYearAscPeriodMonthAsc(acc.getId())));
        }
        return out;
    }

    // ---------- Internals ----------

    /**
     * Recompute every month's running balance in date order, then the period
     * total. Stored rather than derived on read so a settlement is always
     * traceable to the numbers it was made from.
     */
    private ExcessAccumulator recomputeBalances(ExcessAccumulator acc) {
        BigDecimal running = BigDecimal.ZERO;
        for (ExcessAccumulatorMonth m :
                months.findByAccumulatorIdOrderByPeriodYearAscPeriodMonthAsc(acc.getId())) {
            running = running.add(m.getDeltaHours());
            m.setRunningBalance(running);
            months.save(m);
        }
        acc.setBalanceHours(running);
        return accumulators.save(acc);
    }

    private ExcessAccumulator openOrCreate(UUID employeeId, String schemeCode,
                                           BalancingPeriodDef def, int year) {
        return accumulators
                .findByEmployeeIdAndPeriodYearAndPeriodSeq(employeeId, year, def.getPeriodSeq())
                .orElseGet(() -> {
                    ExcessAccumulator acc = new ExcessAccumulator();
                    acc.setEmployeeId(employeeId);
                    acc.setSchemeCode(schemeCode);
                    acc.setPeriodYear(year);
                    acc.setPeriodSeq(def.getPeriodSeq());
                    acc.setPeriodStart(def.startOn(year));
                    acc.setPeriodEnd(def.endOn(year));
                    acc.setSettlementYear(year);
                    acc.setSettlementMonth(def.getSettlementMonth());
                    acc.setStatus(ExcessAccumulator.OPEN);
                    acc.setBalanceHours(BigDecimal.ZERO);
                    return accumulators.save(acc);
                });
    }

    /** Free-text callers (tests, scripts) land in MANUAL rather than failing a check. */
    private static String normaliseSource(String source) {
        if ("PERIOD_LOCK".equals(source) || "REPOST".equals(source)) return source;
        return "MANUAL";
    }

    private BalancingPeriodDef periodFor(String schemeCode, int month) {
        return periodDefs.findBySchemeCodeOrderByPeriodSeqAsc(schemeCode).stream()
                .filter(d -> d.covers(month))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(
                        "Balancing scheme " + schemeCode + " has no period covering month "
                                + month + ". Every month has to fall in exactly one period or "
                                + "hours would go unaccounted."));
    }
}
