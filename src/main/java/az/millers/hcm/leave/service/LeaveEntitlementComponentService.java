package az.millers.hcm.leave.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.domain.EmployeeDependent;
import az.millers.hcm.corehr.repo.EmployeeDependentRepository;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.leave.domain.EntitlementComponentCode;
import az.millers.hcm.leave.domain.EntitlementComponentSource;
import az.millers.hcm.leave.domain.LeaveBalance;
import az.millers.hcm.leave.domain.LeaveEntitlementComponent;
import az.millers.hcm.leave.domain.LeaveType;
import az.millers.hcm.leave.domain.LedgerTxType;
import az.millers.hcm.leave.repo.LeaveBalanceRepository;
import az.millers.hcm.leave.repo.LeaveEntitlementComponentRepository;
import az.millers.hcm.leave.repo.LeaveTypeRepository;
import az.millers.hcm.leave.service.entitlement.EntitlementComponentResolver;
import az.millers.hcm.leave.service.entitlement.EntitlementContext;
import az.millers.hcm.leave.service.entitlement.ResolvedComponent;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.staffing.domain.Position;
import az.millers.hcm.staffing.repo.PositionRepository;

/**
 * M151 — computes the itemised annual leave entitlement and folds it into the
 * balance.
 *
 * <p>Two invariants hold the design together:
 *
 * <ol>
 *   <li><b>Components explain, they never move a balance on their own.</b>
 *       Resolving writes component rows; only {@link #applyToBalance} touches
 *       {@code entitlement_days}, and it writes the sum. A wrong component is
 *       therefore visible and fixable before it can affect anyone's leave.</li>
 *   <li><b>DERIVED is owned by the resolvers, MANUAL is owned by HR.</b>
 *       Recalculation rewrites the first and never touches the second, which
 *       is what lets blood-donation days and one-off grants survive a
 *       recompute.</li>
 * </ol>
 *
 * <p>Drivers are evaluated as at the first day of the leave year, so crossing
 * a threshold mid-year takes effect the following January rather than
 * rewriting a balance that has already been booked against.
 */
@Service
public class LeaveEntitlementComponentService {

    private static final String MODULE = "LEAVE";
    private static final String ENTITY = "LeaveEntitlementComponent";

    private final LeaveEntitlementComponentRepository components;
    private final LeaveBalanceRepository balances;
    private final LeaveTypeRepository types;
    private final EmployeeRepository employees;
    private final EmployeeDependentRepository dependents;
    private final PositionRepository positions;
    private final LeaveLedgerService ledger;
    private final AuditService audit;
    private final CurrentRequest currentRequest;
    private final List<EntitlementComponentResolver> resolvers;

    public LeaveEntitlementComponentService(
            LeaveEntitlementComponentRepository components,
            LeaveBalanceRepository balances,
            LeaveTypeRepository types,
            EmployeeRepository employees,
            EmployeeDependentRepository dependents,
            PositionRepository positions,
            LeaveLedgerService ledger,
            AuditService audit,
            CurrentRequest currentRequest,
            List<EntitlementComponentResolver> resolvers) {
        this.components = components;
        this.balances = balances;
        this.types = types;
        this.employees = employees;
        this.dependents = dependents;
        this.positions = positions;
        this.ledger = ledger;
        this.audit = audit;
        this.currentRequest = currentRequest;
        this.resolvers = resolvers;
    }

    // ── Read ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<LeaveEntitlementComponent> breakdown(UUID employeeId, UUID leaveTypeId, int year) {
        return components.findByEmployeeIdAndLeaveTypeIdAndYearOrderByComponentCode(
                employeeId, leaveTypeId, year);
    }

    /** Sum of every component — what {@code entitlement_days} should equal. */
    @Transactional(readOnly = true)
    public BigDecimal total(UUID employeeId, UUID leaveTypeId, int year) {
        return sum(breakdown(employeeId, leaveTypeId, year));
    }

    // ── Recalculate ─────────────────────────────────────────────────────

    /**
     * Re-derive every DERIVED component for one employee/type/year and write
     * the new total into the balance. MANUAL rows are read but never written.
     *
     * @return the component rows as they stand after the recalculation
     */
    @Transactional
    public List<LeaveEntitlementComponent> recalculate(UUID employeeId, UUID leaveTypeId, int year) {
        LeaveType type = types.findById(leaveTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave type not found: " + leaveTypeId));
        if (!type.isEntitlementComponentsEnabled()) {
            throw new BadRequestException(
                    "Leave type " + type.getCode() + " does not use the component model");
        }
        Employee employee = employees.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));

        EntitlementContext ctx = contextFor(employee, type, year);

        // Resolve first, write second: a resolver that throws must not leave
        // the breakdown half-rewritten and the balance describing neither the
        // old nor the new state.
        Map<EntitlementComponentCode, ResolvedComponent> resolved =
                new EnumMap<>(EntitlementComponentCode.class);
        for (EntitlementComponentResolver resolver : resolvers) {
            resolver.resolve(ctx).ifPresent(r -> resolved.put(r.code(), r));
        }

        List<LeaveEntitlementComponent> existing =
                components.findByEmployeeIdAndLeaveTypeIdAndYearOrderByComponentCode(
                        employeeId, leaveTypeId, year);
        Map<EntitlementComponentCode, LeaveEntitlementComponent> byCode = new HashMap<>();
        existing.forEach(c -> byCode.put(c.getComponentCode(), c));

        String actor = currentRequest.username();
        OffsetDateTime now = OffsetDateTime.now();
        List<LeaveEntitlementComponent> written = new ArrayList<>();

        for (ResolvedComponent r : resolved.values()) {
            LeaveEntitlementComponent row = byCode.get(r.code());
            if (row != null && row.getSource() == EntitlementComponentSource.MANUAL) {
                // HR has taken this component over. Leave it exactly as it is —
                // silently reverting a deliberate override to a derived value
                // is how a hand-granted entitlement disappears overnight.
                written.add(row);
                continue;
            }
            if (row == null) {
                row = new LeaveEntitlementComponent();
                row.setEmployeeId(employeeId);
                row.setLeaveTypeId(leaveTypeId);
                row.setYear(year);
                row.setComponentCode(r.code());
                row.setSource(EntitlementComponentSource.DERIVED);
                row.setCreatedBy(actor);
            }
            row.setDays(r.days());
            row.setBasis(r.basis());
            row.setComputedAt(now);
            row.setUpdatedBy(actor);
            written.add(components.save(row));
        }

        // A DERIVED row whose rule no longer applies (transfer off a hazardous
        // position, last child aged out) must go, or the employee keeps
        // earning an entitlement they no longer qualify for.
        for (LeaveEntitlementComponent row : existing) {
            boolean stillDerived = resolved.containsKey(row.getComponentCode());
            if (!stillDerived && row.getSource() == EntitlementComponentSource.DERIVED) {
                components.delete(row);
            } else if (!stillDerived) {
                written.add(row);   // MANUAL row with no resolver — keep it
            }
        }

        applyToBalance(employeeId, leaveTypeId, year, written);
        return written;
    }

    // ── Manual overrides ────────────────────────────────────────────────

    /**
     * Set (or clear) a MANUAL component. This is the only way to record a
     * component with no derivable driver — blood donation — and the escape
     * hatch for a case the resolvers cannot see, such as a lone father who
     * qualifies under Art. 117.
     *
     * @param days null removes the override
     */
    @Transactional
    public List<LeaveEntitlementComponent> setManual(UUID employeeId, UUID leaveTypeId, int year,
                                                      EntitlementComponentCode code,
                                                      BigDecimal days, String basis) {
        if (days != null && days.signum() < 0) {
            throw new BadRequestException("A component cannot grant negative days");
        }
        if (days != null && (basis == null || basis.isBlank())) {
            // Every component has to be defensible in an inspection, and a
            // hand-entered one has no rule to point at — so the reason is not
            // optional here.
            throw new BadRequestException("A manual component requires a reason");
        }

        Optional<LeaveEntitlementComponent> found =
                components.findByEmployeeIdAndLeaveTypeIdAndYearAndComponentCode(
                        employeeId, leaveTypeId, year, code);

        if (days == null) {
            found.ifPresent(components::delete);
        } else {
            LeaveEntitlementComponent row = found.orElseGet(() -> {
                LeaveEntitlementComponent c = new LeaveEntitlementComponent();
                c.setEmployeeId(employeeId);
                c.setLeaveTypeId(leaveTypeId);
                c.setYear(year);
                c.setComponentCode(code);
                c.setCreatedBy(currentRequest.username());
                return c;
            });
            row.setSource(EntitlementComponentSource.MANUAL);
            row.setDays(days);
            row.setBasis(basis);
            row.setComputedAt(null);
            row.setUpdatedBy(currentRequest.username());
            components.save(row);
        }

        List<LeaveEntitlementComponent> after = breakdown(employeeId, leaveTypeId, year);
        applyToBalance(employeeId, leaveTypeId, year, after);

        audit.record(MODULE, ENTITY, employeeId.toString(), "MANUAL_COMPONENT_SET",
                null, Map.of("leaveTypeId", leaveTypeId.toString(),
                             "year", year,
                             "component", code.name(),
                             "days", days == null ? "cleared" : days.toPlainString(),
                             "basis", basis == null ? "" : basis));
        return after;
    }

    // ── Internals ───────────────────────────────────────────────────────

    private EntitlementContext contextFor(Employee employee, LeaveType type, int year) {
        Position position = employee.getPositionId() == null ? null
                : positions.findById(employee.getPositionId()).orElse(null);
        List<EmployeeDependent> deps =
                dependents.findByEmployeeIdAndActiveTrueOrderByRelationshipTypeAsc(employee.getId());
        // First day of the leave year — see the class javadoc on why this is
        // not today().
        return new EntitlementContext(employee, type, year, position, deps,
                LocalDate.of(year, 1, 1));
    }

    /**
     * Write the component sum into {@code entitlement_days} and record the
     * delta on the ledger. No-ops when the total already matches, so a
     * recalculation that changes nothing does not litter the ledger.
     */
    private void applyToBalance(UUID employeeId, UUID leaveTypeId, int year,
                                 List<LeaveEntitlementComponent> rows) {
        BigDecimal total = sum(rows);
        LeaveBalance balance = balances
                .findByEmployeeIdAndLeaveTypeIdAndYear(employeeId, leaveTypeId, year)
                .orElse(null);
        if (balance == null) {
            // No balance materialised yet — LeaveBalanceService creates it on
            // first use and a recalculation will follow. Nothing to write.
            return;
        }

        BigDecimal before = balance.getEntitlementDays();
        if (before.compareTo(total) == 0) {
            return;
        }

        balance.setEntitlementDays(total);
        balance.setLastRecalculatedAt(OffsetDateTime.now());
        LeaveBalance saved = balances.save(balance);

        ledger.record(employeeId, leaveTypeId, year,
                LedgerTxType.ADJUSTMENT, total.subtract(before),
                LocalDate.of(year, 1, 1),
                "ENTITLEMENT_COMPONENTS", null, saved.remaining(),
                "Entitlement recomputed from components: " + describe(rows),
                currentRequest.username());

        audit.record(MODULE, ENTITY, employeeId.toString(), "ENTITLEMENT_RECALCULATED",
                Map.of("entitlementDays", before),
                Map.of("entitlementDays", total,
                       "leaveTypeId", leaveTypeId.toString(),
                       "year", year,
                       "breakdown", describe(rows)));
    }

    /**
     * Sum of the components that form the annual vacation entitlement.
     *
     * <p>Blood-donation days are excluded — they are earned rest days, not
     * vacation. The customer's register is unambiguous on this: every row
     * whose stated total disagreed with the sum of its own parts disagreed by
     * exactly the blood-donation figure, 10 rows out of 10. Including them
     * would over-grant annual leave to every donor.
     */
    static BigDecimal sum(List<LeaveEntitlementComponent> rows) {
        return rows.stream()
                .filter(c -> c.getComponentCode().countsTowardAnnualEntitlement())
                .map(LeaveEntitlementComponent::getDays)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** "BASE 30 + SENIORITY 2 + HAZARDOUS 6" — readable in the ledger note. */
    private static String describe(List<LeaveEntitlementComponent> rows) {
        return rows.stream()
                .sorted((a, b) -> a.getComponentCode().compareTo(b.getComponentCode()))
                .map(c -> c.getComponentCode() + " " + c.getDays().stripTrailingZeros().toPlainString())
                .reduce((a, b) -> a + " + " + b)
                .orElse("none");
    }
}
