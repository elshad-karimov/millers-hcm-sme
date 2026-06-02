package az.millers.hcm.reporting.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.reporting.api.dto.OrgReportDtos.SpanOfControlReport;
import az.millers.hcm.reporting.api.dto.OrgReportDtos.SpanOfControlRow;

/**
 * Span-of-control analytics (M81 / P2-31-ish reporting).
 *
 * <p>For every employee who has at least one direct report, returns:
 * <ul>
 *   <li>direct report count (one-hop)</li>
 *   <li>transitive descendant count (through {@code Employee.managerId} chain)</li>
 *   <li>depth — longest chain rooted at this employee</li>
 * </ul>
 *
 * <p>Computed in-memory on the assumption that the HCM dataset fits in RAM
 * (PRD § 15 — ≤ 10⁵ employees). For larger tenants, swap to a recursive CTE
 * + window function on Postgres without changing the public shape.
 */
@Service
public class SpanOfControlService {

    private static final int OVERSPAN_THRESHOLD = 10;
    private static final int UNDERSPAN_THRESHOLD = 2;

    private final EmployeeRepository employees;

    public SpanOfControlService(EmployeeRepository employees) {
        this.employees = employees;
    }

    @Transactional(readOnly = true)
    public SpanOfControlReport report() {
        List<Employee> all = employees.findAll();
        Map<UUID, List<UUID>> reportsOf = new HashMap<>();
        Map<UUID, Employee> byId = new HashMap<>();
        for (Employee e : all) {
            byId.put(e.getId(), e);
            if (e.getManagerId() != null) {
                reportsOf.computeIfAbsent(e.getManagerId(), k -> new ArrayList<>()).add(e.getId());
            }
        }

        List<SpanOfControlRow> rows = new ArrayList<>();
        for (Map.Entry<UUID, List<UUID>> entry : reportsOf.entrySet()) {
            UUID managerId = entry.getKey();
            Employee mgr = byId.get(managerId);
            if (mgr == null) continue; // dangling manager reference — skip silently
            int direct = entry.getValue().size();
            int transitive = countDescendants(managerId, reportsOf);
            int depth = depthFrom(managerId, reportsOf);
            String flag = direct >= OVERSPAN_THRESHOLD
                    ? "OVERSPAN"
                    : direct <= UNDERSPAN_THRESHOLD ? "UNDERSPAN" : "OK";
            rows.add(new SpanOfControlRow(
                    managerId, mgr.getEmployeeNo(),
                    mgr.getFirstName() + " " + mgr.getLastName(),
                    mgr.getPositionTitle(),
                    mgr.getDepartmentName(),
                    direct, transitive, depth, flag));
        }
        rows.sort(Comparator.comparingInt(SpanOfControlRow::directReports).reversed());

        long over = rows.stream().filter(r -> "OVERSPAN".equals(r.flag())).count();
        long under = rows.stream().filter(r -> "UNDERSPAN".equals(r.flag())).count();
        return new SpanOfControlReport(
                rows.size(), over, under,
                OVERSPAN_THRESHOLD, UNDERSPAN_THRESHOLD, rows);
    }

    private static int countDescendants(UUID root, Map<UUID, List<UUID>> reportsOf) {
        int total = 0;
        var queue = new java.util.ArrayDeque<UUID>();
        var seen = new java.util.HashSet<UUID>();
        queue.add(root);
        seen.add(root);
        while (!queue.isEmpty()) {
            UUID id = queue.poll();
            List<UUID> children = reportsOf.get(id);
            if (children == null) continue;
            for (UUID c : children) {
                if (seen.add(c)) {
                    total++;
                    queue.add(c);
                }
            }
        }
        return total;
    }

    private static int depthFrom(UUID root, Map<UUID, List<UUID>> reportsOf) {
        // DFS — careful with cycles (should never happen, but defensive).
        return depthFrom(root, reportsOf, new java.util.HashSet<>());
    }

    private static int depthFrom(UUID id, Map<UUID, List<UUID>> reportsOf,
                                  java.util.Set<UUID> seen) {
        if (!seen.add(id)) return 0;
        List<UUID> children = reportsOf.get(id);
        if (children == null || children.isEmpty()) return 0;
        int best = 0;
        for (UUID c : children) {
            best = Math.max(best, 1 + depthFrom(c, reportsOf, seen));
        }
        return best;
    }
}
