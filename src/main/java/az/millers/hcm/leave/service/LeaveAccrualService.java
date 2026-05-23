package az.millers.hcm.leave.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.corehr.domain.EmploymentStatus;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.leave.domain.LeaveBalance;
import az.millers.hcm.leave.domain.LeaveType;
import az.millers.hcm.leave.repo.LeaveBalanceRepository;
import az.millers.hcm.leave.repo.LeaveTypeRepository;

/**
 * Monthly leave-accrual walker (PRD 8.5.2 — milestone 34).
 *
 * <p>On the 1st of every month (cron at 02:00) we iterate every active
 * employee × every active accrual-bearing leave type and bump that
 * (employee, type, year) balance by either the type's explicit
 * {@code monthlyAccrualDays} or, if {@code null}, {@code default/12}.
 *
 * <p>Idempotency: each application of accrual is journalled via a
 * dedicated audit row ({@code LEAVE / LeaveBalance / MONTHLY_ACCRUAL}).
 * The walker first checks the {@link #ACCRUAL_KEY} flag inside
 * {@link LeaveBalance#getLastRecalculatedAt()} via a separate per-period
 * marker carried in audit history. Simpler approach used here: we check
 * the audit log for a marker row keyed on {@code year-month + balance.id}
 * before bumping, so re-runs are no-ops within the same target month.
 *
 * <p>Operationally accessible via
 * {@code POST /api/leave/accruals/run-now?year=&month=&dryRun=}
 * (HR_ADMIN / SYSTEM_ADMIN only) for off-cycle / verification runs.
 */
@Service
public class LeaveAccrualService {

    private static final Logger log = LoggerFactory.getLogger(LeaveAccrualService.class);

    private static final String MODULE = "LEAVE";
    private static final String ENTITY = "LeaveBalance";
    private static final String ACTION = "MONTHLY_ACCRUAL";

    /**
     * Employment statuses considered "still employed" for accrual
     * purposes. PRD 8.5.2 — anyone whose contract is in force still
     * accrues annual leave, including probationers and people currently
     * on leave / business trip. Excludes only terminal states (TERMINATED,
     * RETIRED) and SUSPENDED (suspension freezes the accrual clock per
     * Labour Code §132).
     */
    private static final Set<EmploymentStatus> ACCRUING_STATUSES = EnumSet.of(
            EmploymentStatus.ACTIVE,
            EmploymentStatus.ON_PROBATION,
            EmploymentStatus.ON_LEAVE,
            EmploymentStatus.ON_BUSINESS_TRIP,
            EmploymentStatus.CONTRACTOR,
            EmploymentStatus.INTERN);

    private final LeaveTypeRepository leaveTypes;
    private final LeaveBalanceRepository balances;
    private final EmployeeRepository employees;
    private final AuditService audit;

    public LeaveAccrualService(LeaveTypeRepository leaveTypes,
                               LeaveBalanceRepository balances,
                               EmployeeRepository employees,
                               AuditService audit) {
        this.leaveTypes = leaveTypes;
        this.balances = balances;
        this.employees = employees;
        this.audit = audit;
    }

    /**
     * Result envelope returned by both the scheduled walker and the
     * admin trigger. Counts make verification trivial.
     */
    public record AccrualResult(
            int year,
            int month,
            boolean dryRun,
            int activeEmployees,
            int eligibleTypes,
            int balancesCredited,
            int balancesSkipped,
            BigDecimal totalDaysCredited) {}

    /**
     * Matches a {@code "period":"YYYY-MM"} JSON marker tolerating the
     * whitespace PostgreSQL JSONB inserts on round-trip (Jackson writes
     * compact JSON, but JSONB normalises to {@code "key": "value"} on
     * read). Used by {@link #alreadyAccrued} to keep the walker
     * idempotent across re-runs.
     */
    private static Pattern periodMarker(String periodKey) {
        return Pattern.compile("\"period\"\\s*:\\s*\"" + Pattern.quote(periodKey) + "\"");
    }

    /**
     * Cron: 02:00 on the 1st of every month. Uses the system clock's
     * current year/month so the walker grants accrual for "this month".
     * Public + idempotent — re-runs are no-ops on already-credited
     * (balance, period) pairs.
     */
    @Scheduled(cron = "${hcm.leave.accrual.cron:0 0 2 1 * *}")
    @Transactional
    public AccrualResult runMonthly() {
        LocalDate now = LocalDate.now();
        AccrualResult r = accrueForMonth(now.getYear(), now.getMonthValue(), false);
        log.info("Monthly leave accrual: {}", r);
        return r;
    }

    /**
     * Credits monthly accrual for ({@code year}, {@code month}) across
     * every active employee × every active accrual-bearing type.
     *
     * @param year   target year (used as the {@code leave_balance.year} key)
     * @param month  target month — used for the audit marker so the same
     *               (balance, period) pair can't be credited twice
     * @param dryRun when {@code true}, balances are not written and no
     *               audit rows are emitted; counts still come back
     */
    @Transactional
    public AccrualResult accrueForMonth(int year, int month, boolean dryRun) {
        List<UUID> activeEmpIds = employees.findIdsByEmploymentStatusIn(ACCRUING_STATUSES);
        List<LeaveType> eligibleTypes = leaveTypes
                .findByActiveTrueAndAccruesMonthlyTrueOrderByCodeAsc();

        int credited = 0;
        int skipped = 0;
        BigDecimal total = BigDecimal.ZERO;

        // Pre-compute the per-type bump once so we don't re-evaluate the
        // fallback for every employee.
        Map<UUID, BigDecimal> bumpsByType = new HashMap<>();
        for (LeaveType t : eligibleTypes) {
            bumpsByType.put(t.getId(), monthlyBumpFor(t));
        }

        String periodKey = String.format("%04d-%02d", year, month);

        for (UUID empId : activeEmpIds) {
            for (LeaveType t : eligibleTypes) {
                BigDecimal bump = bumpsByType.get(t.getId());
                if (bump.signum() == 0) {
                    skipped++;
                    continue;
                }
                LeaveBalance b = ensureBalance(empId, t, year);
                if (alreadyAccrued(b.getId(), periodKey)) {
                    skipped++;
                    continue;
                }
                if (!dryRun) {
                    BigDecimal before = b.getEntitlementDays();
                    b.setEntitlementDays(before.add(bump));
                    b.setLastRecalculatedAt(OffsetDateTime.now());
                    balances.save(b);
                    Map<String, Object> delta = Map.of(
                            "period", periodKey,
                            "employeeId", empId.toString(),
                            "leaveTypeId", t.getId().toString(),
                            "leaveTypeCode", t.getCode(),
                            "year", year,
                            "deltaDays", bump,
                            "entitlementBefore", before,
                            "entitlementAfter", b.getEntitlementDays());
                    audit.record(MODULE, ENTITY, b.getId().toString(),
                            ACTION, null, delta);
                }
                credited++;
                total = total.add(bump);
            }
        }

        return new AccrualResult(
                year, month, dryRun,
                activeEmpIds.size(),
                eligibleTypes.size(),
                credited,
                skipped,
                total);
    }

    /**
     * Picks the bump amount: explicit {@code monthlyAccrualDays} on the
     * type wins; otherwise we fall back to {@code default/12}. {@code 0}
     * when both are absent — meaning the type opted into monthly accrual
     * without a configured bank (e.g. SICK), and the walker is a no-op.
     */
    BigDecimal monthlyBumpFor(LeaveType t) {
        if (t.getMonthlyAccrualDays() != null) {
            return t.getMonthlyAccrualDays();
        }
        if (t.getDefaultAnnualEntitlementDays() != null
                && t.getDefaultAnnualEntitlementDays().signum() > 0) {
            return t.getDefaultAnnualEntitlementDays()
                    .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    /**
     * Lazily materialise the {@code leave_balance} row with zero
     * entitlement — the accrual bump below is what credits it. The
     * default annual entitlement is NOT pre-loaded for monthly types
     * because that's exactly what monthly accrual is replacing.
     */
    private LeaveBalance ensureBalance(UUID employeeId, LeaveType type, int year) {
        Optional<LeaveBalance> existing = balances
                .findByEmployeeIdAndLeaveTypeIdAndYear(employeeId, type.getId(), year);
        if (existing.isPresent()) {
            return existing.get();
        }
        LeaveBalance b = new LeaveBalance();
        b.setEmployeeId(employeeId);
        b.setLeaveTypeId(type.getId());
        b.setYear(year);
        b.setEntitlementDays(BigDecimal.ZERO);
        b.setLastRecalculatedAt(OffsetDateTime.now());
        return balances.save(b);
    }

    /**
     * Returns true iff the audit log already has a MONTHLY_ACCRUAL row
     * for this {@code (balance, period)} pair. Cheap because audit log
     * is indexed on (entity_name, entity_id) and the per-balance history
     * is at most one row per month.
     */
    private boolean alreadyAccrued(UUID balanceId, String periodKey) {
        Pattern marker = periodMarker(periodKey);
        return audit.history(ENTITY, balanceId.toString()).stream()
                .filter(a -> ACTION.equals(a.getAction()))
                .map(a -> a.getNewValue() == null ? "" : a.getNewValue())
                .anyMatch(json -> marker.matcher(json).find());
    }
}
