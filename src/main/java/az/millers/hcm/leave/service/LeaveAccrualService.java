package az.millers.hcm.leave.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import az.millers.hcm.leave.api.dto.SeniorityBracket;
import az.millers.hcm.leave.domain.LedgerTxType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditIdempotency;
import az.millers.hcm.audit.AuditService;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.domain.EmploymentStatus;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.leave.domain.LeaveBalance;
import az.millers.hcm.leave.domain.LeaveGroupEntitlement;
import az.millers.hcm.leave.domain.LeaveType;
import az.millers.hcm.leave.repo.LeaveBalanceRepository;
import az.millers.hcm.leave.repo.LeaveTypeRepository;
import az.millers.hcm.organization.domain.VersionStatus;
import az.millers.hcm.organization.repo.StructureVersionRepository;
import az.millers.hcm.organization.service.OrgUnitPolicyService;

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
    private final AuditIdempotency auditIdempotency;
    private final LeaveGroupService leaveGroupService;
    private final OrgUnitPolicyService orgPolicies;
    private final StructureVersionRepository orgVersions;
    private final LeaveLedgerService ledger;

    public LeaveAccrualService(LeaveTypeRepository leaveTypes,
                               LeaveBalanceRepository balances,
                               EmployeeRepository employees,
                               AuditService audit,
                               AuditIdempotency auditIdempotency,
                               LeaveGroupService leaveGroupService,
                               OrgUnitPolicyService orgPolicies,
                               StructureVersionRepository orgVersions,
                               LeaveLedgerService ledger) {
        this.leaveTypes = leaveTypes;
        this.balances = balances;
        this.employees = employees;
        this.audit = audit;
        this.auditIdempotency = auditIdempotency;
        this.leaveGroupService = leaveGroupService;
        this.orgPolicies = orgPolicies;
        this.orgVersions = orgVersions;
        this.ledger = ledger;
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

        // M47: load hire dates so the seniority-bracket lookup can compute tenure.
        // Guard against an empty IN clause (some JDBC drivers reject it).
        Map<UUID, LocalDate> hireDates = new HashMap<>();
        if (!activeEmpIds.isEmpty()) {
            employees.findIdAndHireDateByIdIn(activeEmpIds)
                    .forEach(row -> hireDates.put((UUID) row[0], (LocalDate) row[1]));
        }

        // M66 / P1-08: load each employee's leaveGroupId so the override
        // lookup below can short-circuit to the per-group entitlement when
        // one is configured. Null = fall through to the default group inside
        // LeaveGroupService.resolveEntitlementSource.
        Map<UUID, UUID> leaveGroups = new HashMap<>();
        if (!activeEmpIds.isEmpty()) {
            employees.findIdAndLeaveGroupIdByIdIn(activeEmpIds)
                    .forEach(row -> leaveGroups.put((UUID) row[0], (UUID) row[1]));
        }

        // M82: when an employee's own leaveGroupId is null, walk up the
        // active org-structure version's policy chain. orgUnitPairs holds
        // (employeeId → orgUnitId); activeVersionId is null when no version
        // is ACTIVE (fresh tenants), in which case we just skip the walk.
        Map<UUID, UUID> orgUnitPairs = new HashMap<>();
        if (!activeEmpIds.isEmpty()) {
            employees.findIdAndOrgUnitIdByIdIn(activeEmpIds)
                    .forEach(row -> orgUnitPairs.put((UUID) row[0], (UUID) row[1]));
        }
        final UUID activeVersionId = orgVersions.findFirstByStatus(VersionStatus.ACTIVE)
                .map(v -> v.getId()).orElse(null);

        // First day of the target period — used as the "as of" date for tenure.
        LocalDate periodDate = LocalDate.of(year, month, 1);

        int credited = 0;
        int skipped = 0;
        BigDecimal total = BigDecimal.ZERO;

        String periodKey = String.format("%04d-%02d", year, month);

        for (UUID empId : activeEmpIds) {
            LocalDate hireDate = hireDates.get(empId); // may be null for legacy data
            UUID rowLeaveGroup = leaveGroups.get(empId); // may be null
            // M82: walk the org-tree policy chain when the employee row itself
            // doesn't pin a group. activeVersionId may be null on fresh
            // tenants — then resolveLeaveGroup() returns the row override or
            // null and we fall through to LeaveGroupService's default group.
            UUID leaveGroupId = activeVersionId == null
                    ? rowLeaveGroup
                    : orgPolicies.resolveLeaveGroup(
                            rowLeaveGroup, orgUnitPairs.get(empId), activeVersionId);
            for (LeaveType t : eligibleTypes) {
                // M66: check per-(group, type) override first. Returns null when
                // no override exists, in which case the existing priority chain
                // (brackets → monthlyAccrualDays → default/12) applies unchanged.
                LeaveGroupEntitlement override = leaveGroupService
                        .resolveEntitlementSource(leaveGroupId, t.getId());
                // Per-employee bump: seniority brackets (if configured) win over
                // the flat monthlyAccrualDays / default/12 fallback chain.
                BigDecimal bump = monthlyBumpFor(t, override, hireDate, periodDate);
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

                    Map<String, Object> delta = new HashMap<>();
                    delta.put("period", periodKey);
                    delta.put("employeeId", empId.toString());
                    delta.put("leaveTypeId", t.getId().toString());
                    delta.put("leaveTypeCode", t.getCode());
                    delta.put("year", year);
                    delta.put("deltaDays", bump);
                    delta.put("entitlementBefore", before);
                    delta.put("entitlementAfter", b.getEntitlementDays());
                    if (hasBrackets(t) && hireDate != null) {
                        long tenure = Math.max(0L,
                                ChronoUnit.YEARS.between(hireDate, periodDate));
                        delta.put("seniorityYears", tenure);
                    }
                    audit.record(MODULE, ENTITY, b.getId().toString(),
                            ACTION, null, delta);

                    ledger.record(empId, t.getId(), year,
                            LedgerTxType.ACCRUAL, bump, periodDate.withDayOfMonth(1),
                            "ACCRUAL", periodKey, b.remaining(),
                            "Monthly accrual for " + periodKey, "system");
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
     * Convenience overload for callers that don't have a hire date
     * (e.g. dry-run preview where the employee set isn't fully loaded).
     * Skips brackets, no override, falls back to the flat
     * {@code monthlyAccrualDays / default/12} chain.
     */
    BigDecimal monthlyBumpFor(LeaveType t) {
        return monthlyBumpFor(t, null, null, LocalDate.now());
    }

    /**
     * Backwards-compatible overload — same semantics as before M66, no override.
     */
    BigDecimal monthlyBumpFor(LeaveType t, LocalDate hireDate, LocalDate periodDate) {
        return monthlyBumpFor(t, null, hireDate, periodDate);
    }

    /**
     * Per-employee monthly bump, honouring seniority brackets (M47) and
     * per-(group, type) overrides (M66).
     *
     * <p>Priority:
     * <ol>
     *   <li><strong>Override seniority brackets</strong> (M66) — if the
     *       employee's group has an override row WITH non-empty
     *       {@code seniority_brackets_json}, those take precedence over the
     *       type's own brackets.</li>
     *   <li><strong>Type seniority brackets</strong> (M47) — when set and the
     *       employee's hire date is known, the matching bracket's
     *       {@code annualDays / 12} wins.</li>
     *   <li><strong>Override monthly_accrual_days</strong> (M66) — explicit
     *       per-month amount from the group override.</li>
     *   <li><strong>Type monthly_accrual_days</strong> — explicit per-month
     *       amount from the leave type.</li>
     *   <li><strong>Override annual_entitlement_days / 12</strong> (M66).</li>
     *   <li><strong>Type default_annual_entitlement_days / 12</strong>.</li>
     *   <li>Zero — when no source is configured.</li>
     * </ol>
     *
     * <p>The override layer is consulted before the corresponding type field
     * at every level, so a group can override <em>only</em> the annual days
     * budget while inheriting the type's seniority schedule (or vice versa).
     *
     * @param t           the leave type (carries default entitlement values)
     * @param override    per-(group, type) override row; {@code null} = no override
     * @param hireDate    employee hire date; {@code null} skips brackets
     * @param periodDate  the 1st of the target accrual month (tenure "as of" date)
     */
    BigDecimal monthlyBumpFor(LeaveType t, LeaveGroupEntitlement override,
                               LocalDate hireDate, LocalDate periodDate) {
        // Priority 1+2: brackets (override wins over type)
        List<SeniorityBracket> brackets = effectiveBrackets(t, override);
        if (brackets != null && !brackets.isEmpty() && hireDate != null) {
            long tenureYears = Math.max(0L,
                    ChronoUnit.YEARS.between(hireDate, periodDate));
            Optional<SeniorityBracket> matched = brackets.stream()
                    .filter(b -> tenureYears >= b.yearsMin()
                              && (b.yearsMax() == null || tenureYears <= b.yearsMax()))
                    .findFirst();
            if (matched.isPresent()) {
                return matched.get().annualDays()
                        .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            }
            log.warn("No seniority bracket matched tenure={} years for type={} emp hire={}; "
                     + "falling back to flat accrual", tenureYears, t.getCode(), hireDate);
        }

        // Priority 3+4: explicit monthly amount (override wins over type)
        BigDecimal monthly = override != null && override.getMonthlyAccrualDays() != null
                ? override.getMonthlyAccrualDays()
                : t.getMonthlyAccrualDays();
        if (monthly != null) {
            return monthly;
        }

        // Priority 5+6: annual / 12 (override wins over type)
        BigDecimal annual = override != null && override.getAnnualEntitlementDays() != null
                ? override.getAnnualEntitlementDays()
                : t.getDefaultAnnualEntitlementDays();
        if (annual != null && annual.signum() > 0) {
            return annual.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
        }

        return BigDecimal.ZERO;
    }

    /**
     * Override brackets win over type brackets when populated. Returns
     * whichever non-empty list applies, or null.
     */
    private List<SeniorityBracket> effectiveBrackets(LeaveType t, LeaveGroupEntitlement override) {
        if (override != null
                && override.getSeniorityBrackets() != null
                && !override.getSeniorityBrackets().isEmpty()) {
            return override.getSeniorityBrackets();
        }
        return t.getSeniorityBrackets();
    }

    /** Returns true iff the leave type has at least one seniority bracket (M47). */
    private boolean hasBrackets(LeaveType t) {
        return t.getSeniorityBrackets() != null && !t.getSeniorityBrackets().isEmpty();
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
     * for this {@code (balance, period)} pair. Delegates to the shared
     * {@link AuditIdempotency} helper so the regex-over-JSON dance lives
     * in exactly one place across schedulers.
     */
    private boolean alreadyAccrued(UUID balanceId, String periodKey) {
        return auditIdempotency.hasMarker(ENTITY, balanceId.toString(), ACTION,
                Map.of("period", periodKey));
    }
}
