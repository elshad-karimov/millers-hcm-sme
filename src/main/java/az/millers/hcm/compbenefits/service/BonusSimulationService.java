package az.millers.hcm.compbenefits.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.compbenefits.api.dto.SimulationDtos.AllocationRow;
import az.millers.hcm.compbenefits.api.dto.SimulationDtos.SimulationRequest;
import az.millers.hcm.compbenefits.api.dto.SimulationDtos.SimulationResponse;
import az.millers.hcm.compbenefits.domain.CompCycle;
import az.millers.hcm.compbenefits.domain.CompProposal;
import az.millers.hcm.compbenefits.repo.CompCycleRepository;
import az.millers.hcm.compbenefits.repo.CompProposalRepository;
import az.millers.hcm.compbenefits.service.BonusSimulator.EmployeeAllocation;
import az.millers.hcm.compbenefits.service.BonusSimulator.EmployeeContext;
import az.millers.hcm.compbenefits.service.BonusSimulator.SimulationConfig;
import az.millers.hcm.compbenefits.service.BonusSimulator.SimulationResult;

/**
 * M129 — loads the cycle's proposal cohort + employee context and runs
 * the pure-static simulator. Performance rating + tenure are pulled
 * with one JDBC round-trip each so the simulator doesn't blow up on a
 * 1000-person cycle.
 */
@Service
public class BonusSimulationService {

    private final CompCycleRepository cycles;
    private final CompProposalRepository proposals;
    private final NamedParameterJdbcTemplate jdbc;

    public BonusSimulationService(CompCycleRepository cycles,
                                  CompProposalRepository proposals,
                                  NamedParameterJdbcTemplate jdbc) {
        this.cycles = cycles;
        this.proposals = proposals;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public SimulationResponse simulate(UUID cycleId, SimulationRequest req) {
        CompCycle cycle = cycles.findById(cycleId).orElseThrow(
                () -> new ResourceNotFoundException("Cycle not found: " + cycleId));

        List<CompProposal> cycleProposals = proposals.findByCycleIdOrderByProposedAtDesc(cycleId);
        if (cycleProposals.isEmpty()) {
            return new SimulationResponse(cycleId,
                    pickPool(req, cycle), BigDecimal.ZERO, pickPool(req, cycle),
                    0, 0, List.of());
        }

        // Pull employee identity + hire date for tenure.
        List<UUID> empIds = cycleProposals.stream().map(CompProposal::getEmployeeId).distinct().toList();
        Map<UUID, EmpData> empData = loadEmployeeData(empIds);
        // Pull each employee's most recent manager_rating from
        // performance_review across all cycles — the freshest signal.
        Map<UUID, BigDecimal> ratings = loadRatings(empIds);

        LocalDate today = LocalDate.now();
        List<EmployeeContext> contexts = new ArrayList<>(cycleProposals.size());
        for (CompProposal p : cycleProposals) {
            EmpData e = empData.get(p.getEmployeeId());
            String empNo = e == null ? null : e.employeeNo;
            String name = e == null ? null : e.firstName + " " + e.lastName;
            int tenure = e == null || e.hireDate == null
                    ? 0
                    : (int) ChronoUnit.MONTHS.between(e.hireDate, today);
            BigDecimal rating = ratings.getOrDefault(p.getEmployeeId(), null);
            BigDecimal base = p.getCurrentSalary() == null
                    ? BigDecimal.ZERO : p.getCurrentSalary();
            contexts.add(new EmployeeContext(p.getEmployeeId(), base, rating, tenure));
            // Stash the meta for the response row.
            empNo(p.getEmployeeId(), empNo, name);
        }

        SimulationConfig config = new SimulationConfig(
                pickPool(req, cycle),
                nz(req == null ? null : req.performanceWeight(), BigDecimal.valueOf(0.5)),
                nz(req == null ? null : req.tenureWeight(),      BigDecimal.valueOf(0.2)),
                nz(req == null ? null : req.baseWeight(),        BigDecimal.valueOf(0.3)),
                nz(req == null ? null : req.floorPercent(),      BigDecimal.ZERO),
                nz(req == null ? null : req.capPercent(),        BigDecimal.ZERO));

        SimulationResult result = BonusSimulator.simulate(contexts, config);

        List<AllocationRow> rows = new ArrayList<>(result.allocations().size());
        for (EmployeeAllocation a : result.allocations()) {
            EmpData e = empData.get(a.employeeId());
            BigDecimal rating = ratings.get(a.employeeId());
            int tenure = e == null || e.hireDate == null
                    ? 0
                    : (int) ChronoUnit.MONTHS.between(e.hireDate, today);
            rows.add(new AllocationRow(
                    a.employeeId(),
                    e == null ? null : e.employeeNo,
                    e == null ? null : (e.firstName + " " + e.lastName),
                    a.baseSalary(),
                    rating,
                    tenure,
                    a.score(),
                    a.weight(),
                    a.allocatedBonus(),
                    a.percentOfBase(),
                    a.clamped()));
        }
        return new SimulationResponse(
                cycleId,
                result.poolTotal(),
                result.totalAllocated(),
                result.residual(),
                result.eligibleCount(),
                result.clampedCount(),
                rows);
    }

    private static BigDecimal pickPool(SimulationRequest req, CompCycle cycle) {
        if (req != null && req.poolTotal() != null && req.poolTotal().signum() > 0) {
            return req.poolTotal();
        }
        return cycle.getPoolTotal() == null ? BigDecimal.ZERO : cycle.getPoolTotal();
    }

    private static BigDecimal nz(BigDecimal v, BigDecimal dflt) {
        return v == null ? dflt : v;
    }

    private record EmpData(String employeeNo, String firstName, String lastName, LocalDate hireDate) {}

    private Map<UUID, EmpData> loadEmployeeData(List<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<UUID, EmpData> out = new HashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT id::text AS id, employee_no, first_name, last_name, hire_date "
                + "  FROM core_hr.employee WHERE id IN (:ids)",
                new MapSqlParameterSource("ids", ids))) {
            UUID id = UUID.fromString((String) row.get("id"));
            LocalDate hd = row.get("hire_date") == null
                    ? null
                    : ((java.sql.Date) row.get("hire_date")).toLocalDate();
            out.put(id, new EmpData(
                    (String) row.get("employee_no"),
                    (String) row.get("first_name"),
                    (String) row.get("last_name"),
                    hd));
        }
        return out;
    }

    /**
     * Latest manager_rating across all performance_review rows per
     * employee. Uses a window-ish max-by-time pattern that PostgreSQL
     * runs as one pass.
     */
    private Map<UUID, BigDecimal> loadRatings(List<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<UUID, BigDecimal> out = new HashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT DISTINCT ON (employee_id) employee_id::text AS id, manager_rating "
                + "  FROM performance.performance_review "
                + " WHERE employee_id IN (:ids) AND manager_rating IS NOT NULL"
                + " ORDER BY employee_id, updated_at DESC NULLS LAST",
                new MapSqlParameterSource("ids", ids))) {
            Object rating = row.get("manager_rating");
            if (rating != null) {
                out.put(UUID.fromString((String) row.get("id")), (BigDecimal) rating);
            }
        }
        return out;
    }

    // Tiny no-op so the per-employee identity is loaded once per row even
    // when no rating row exists. Kept as a method for clarity.
    @SuppressWarnings("unused")
    private static void empNo(UUID id, String empNo, String name) { /* noop */ }
}
