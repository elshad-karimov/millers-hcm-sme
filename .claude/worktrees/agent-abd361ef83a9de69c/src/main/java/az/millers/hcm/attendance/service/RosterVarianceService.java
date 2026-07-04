package az.millers.hcm.attendance.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.attendance.api.dto.VarianceDtos.EmployeeRoll;
import az.millers.hcm.attendance.api.dto.VarianceDtos.VarianceCell;
import az.millers.hcm.attendance.api.dto.VarianceDtos.VarianceReport;
import az.millers.hcm.attendance.domain.DailySummary;
import az.millers.hcm.attendance.repo.DailySummaryRepository;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.DateWindow;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.security.scope.AccessScopeService;

/**
 * Roster variance reporter (M113).
 *
 * <p>Walks every roster-driven {@link DailySummary} in a date window,
 * categorises each row via {@link VarianceCategory}, and rolls the
 * results up by employee for the dashboard. Returns both the per-cell
 * (employee × date) grid for the heatmap and the per-employee totals
 * for the league table.
 *
 * <p>Access scope: a manager or org-unit-scoped HR specialist sees only
 * the rows they're allowed to see; HR admins / auditors see everything.
 */
@Service
public class RosterVarianceService {

    /** Hard ceiling on the window so a misclick can't pull a year of rows. */
    static final int MAX_WINDOW_DAYS = 92;

    private final DailySummaryRepository summaries;
    private final EmployeeRepository employees;
    private final AccessScopeService accessScope;

    public RosterVarianceService(DailySummaryRepository summaries,
                                  EmployeeRepository employees,
                                  AccessScopeService accessScope) {
        this.summaries = summaries;
        this.employees = employees;
        this.accessScope = accessScope;
    }

    @Transactional(readOnly = true)
    public VarianceReport report(LocalDate from, LocalDate to) {
        validateWindow(from, to);

        // ABAC scope: unrestricted callers see everything; scoped callers get
        // their visible employee set anded in. Empty scope → empty report.
        Set<UUID> scope = accessScope.scopeOrNullForCurrentUser();
        List<DailySummary> rows;
        if (scope == null) {
            rows = summaries.findByWorkDateBetweenOrderByWorkDateAscEmployeeIdAsc(from, to);
        } else if (scope.isEmpty()) {
            return emptyReport(from, to);
        } else {
            rows = summaries.findInWindowForEmployees(from, to, scope);
        }

        // Categorise once; reuse for cells + roll-ups.
        Map<UUID, List<DailySummary>> byEmployee = new HashMap<>();
        Map<UUID, List<VarianceCategory>> categoriesByEmployee = new HashMap<>();
        List<VarianceCell> cells = new ArrayList<>(rows.size());
        Map<VarianceCategory, Integer> totals = newTotals();
        int rosteredRows = 0;
        for (DailySummary s : rows) {
            VarianceCategory cat = VarianceCategory.of(s);
            if (cat == VarianceCategory.NOT_APPLICABLE) continue;
            rosteredRows++;
            totals.merge(cat, 1, Integer::sum);
            byEmployee.computeIfAbsent(s.getEmployeeId(), k -> new ArrayList<>()).add(s);
            categoriesByEmployee.computeIfAbsent(s.getEmployeeId(), k -> new ArrayList<>()).add(cat);
            cells.add(new VarianceCell(
                    s.getEmployeeId(), s.getWorkDate(), cat,
                    s.getLateMinutes(), s.getEarlyMinutes(), s.getOvertimeMinutes()));
        }

        // Decorate employee names once.
        Map<UUID, Employee> empCache = new HashMap<>();
        List<EmployeeRoll> rolls = new ArrayList<>(byEmployee.size());
        for (Map.Entry<UUID, List<DailySummary>> entry : byEmployee.entrySet()) {
            UUID empId = entry.getKey();
            List<DailySummary> empRows = entry.getValue();
            List<VarianceCategory> empCats = categoriesByEmployee.get(empId);
            Employee emp = empCache.computeIfAbsent(empId,
                    id -> employees.findById(id).orElse(null));
            rolls.add(rollFor(empId, emp, empRows, empCats));
        }
        // Worst-variance employees first.
        rolls.sort(Comparator
                .comparingInt(EmployeeRoll::variantDays).reversed()
                .thenComparingInt((EmployeeRoll r) -> r.noShow()).reversed()
                .thenComparing(EmployeeRoll::employeeName,
                        Comparator.nullsLast(String::compareToIgnoreCase)));

        return new VarianceReport(from, to, rosteredRows, totals, rolls, cells);
    }

    /** Package-private — pinned by the unit test. */
    static void validateWindow(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BadRequestException("from and to are required");
        }
        if (to.isBefore(from)) {
            throw new BadRequestException("'to' must be on or after 'from'");
        }
        if (from.plusDays(MAX_WINDOW_DAYS).isBefore(to)) {
            throw new BadRequestException(
                    "Window too wide — max " + MAX_WINDOW_DAYS + " days");
        }
        // Sanity check: the shared DateWindow helper exists for the same
        // half-open invariant used by analytics endpoints; touch it so a
        // future refactor that drops it gets a compile failure here.
        DateWindow.of(from, to.plusDays(1));
    }

    /** Package-private — pinned by the unit test. */
    static EmployeeRoll rollFor(UUID employeeId,
                                 Employee emp,
                                 List<DailySummary> rows,
                                 List<VarianceCategory> cats) {
        Map<VarianceCategory, Integer> counts = newTotals();
        int totalLate = 0;
        int totalEarly = 0;
        int totalOt = 0;
        for (int i = 0; i < rows.size(); i++) {
            DailySummary s = rows.get(i);
            VarianceCategory cat = cats.get(i);
            counts.merge(cat, 1, Integer::sum);
            totalLate += s.getLateMinutes();
            totalEarly += s.getEarlyMinutes();
            totalOt += s.getOvertimeMinutes();
        }
        String name = emp == null ? null : (emp.getFirstName() + " " + emp.getLastName());
        String orgLabel = emp == null ? null : emp.getDepartmentName();
        return new EmployeeRoll(
                employeeId,
                name,
                orgLabel,
                rows.size(),
                counts.getOrDefault(VarianceCategory.ON_TIME, 0),
                counts.getOrDefault(VarianceCategory.LATE, 0),
                counts.getOrDefault(VarianceCategory.EARLY_LEAVE, 0),
                counts.getOrDefault(VarianceCategory.UNPLANNED_OT, 0),
                counts.getOrDefault(VarianceCategory.NO_SHOW, 0),
                totalLate,
                totalEarly,
                totalOt);
    }

    private static Map<VarianceCategory, Integer> newTotals() {
        Map<VarianceCategory, Integer> m = new EnumMap<>(VarianceCategory.class);
        // Pre-seed the actionable buckets so consumers don't have to null-check.
        m.put(VarianceCategory.NO_SHOW, 0);
        m.put(VarianceCategory.LATE, 0);
        m.put(VarianceCategory.EARLY_LEAVE, 0);
        m.put(VarianceCategory.UNPLANNED_OT, 0);
        m.put(VarianceCategory.ON_TIME, 0);
        return m;
    }

    private VarianceReport emptyReport(LocalDate from, LocalDate to) {
        return new VarianceReport(from, to, 0, newTotals(), List.of(), List.of());
    }

    @SuppressWarnings("unused")
    private Optional<Employee> resolveEmp(UUID id) { return employees.findById(id); }
}
