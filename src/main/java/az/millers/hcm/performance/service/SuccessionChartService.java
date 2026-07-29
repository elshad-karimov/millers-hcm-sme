package az.millers.hcm.performance.service;
import az.millers.hcm.common.tenant.TenantContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.performance.api.dto.SuccessionGridDtos.Readiness;
import az.millers.hcm.performance.domain.CriticalPosition;
import az.millers.hcm.performance.domain.SuccessionDevAction;
import az.millers.hcm.performance.domain.SuccessionNomination;
import az.millers.hcm.performance.repo.CriticalPositionRepository;
import az.millers.hcm.performance.repo.DevelopmentPlanRepository;
import az.millers.hcm.performance.repo.SuccessionDevActionRepository;
import az.millers.hcm.performance.repo.SuccessionNominationRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * HCM_16 M415 — replacement chart & coverage summary (PRD §16.5).
 * Shows for each critical position: incumbents + successors (with readiness/
 * risk/dev-plans). Coverage = how many critical positions have 0 / 1 / 2+
 * READY_NOW successors.
 */
@Service
public class SuccessionChartService {

    private final CriticalPositionRepository criticalPositions;
    private final SuccessionNominationRepository nominations;
    private final SuccessionDevActionRepository devActions;
    private final DevelopmentPlanRepository devPlans;
    private final NamedParameterJdbcTemplate jdbc;
    private final CurrentRequest currentRequest;

    public SuccessionChartService(CriticalPositionRepository criticalPositions,
                                   SuccessionNominationRepository nominations,
                                   SuccessionDevActionRepository devActions,
                                   DevelopmentPlanRepository devPlans,
                                   NamedParameterJdbcTemplate jdbc,
                                   CurrentRequest currentRequest) {
        this.criticalPositions = criticalPositions;
        this.nominations = nominations;
        this.devActions = devActions;
        this.devPlans = devPlans;
        this.jdbc = jdbc;
        this.currentRequest = currentRequest;
    }

    /** Replacement chart: all active critical positions + incumbents + successors. */
    @Transactional(readOnly = true)
    public ReplacementChartResponse replacementChart() {
        String tenant = TenantContext.current();
        List<CriticalPosition> crits = criticalPositions.findByTenantIdAndActiveTrueOrderByCreatedAtDesc(tenant);

        // Load all position IDs
        List<UUID> positionIds = crits.stream().map(CriticalPosition::getPositionId).toList();
        if (positionIds.isEmpty()) {
            return new ReplacementChartResponse(List.of(), new CoverageSummary(0, 0, 0));
        }

        // Load position names
        Map<UUID, PositionInfo> posMap = loadPositions(positionIds);

        // Load incumbents (employees currently in these positions)
        Map<UUID, List<EmployeeInfo>> incumbentsMap = loadIncumbents(positionIds);

        // Load nominations for these positions
        Map<UUID, List<SuccessionNomination>> nomsMap = new HashMap<>();
        for (UUID posId : positionIds) {
            nomsMap.put(posId, nominations.findByTenantIdAndPositionIdAndCancelledAtIsNullOrderByCreatedAtDesc(TenantContext.current(), posId));
        }

        // Load dev plans for all nominations
        List<UUID> nomIds = nomsMap.values().stream()
                .flatMap(List::stream)
                .map(SuccessionNomination::getId)
                .toList();
        Map<UUID, List<UUID>> devPlansByNom = loadDevPlans(nomIds);

        // Build position entries
        List<PositionEntry> entries = new ArrayList<>();
        for (CriticalPosition cp : crits) {
            UUID posId = cp.getPositionId();
            PositionInfo pos = posMap.get(posId);
            List<EmployeeInfo> incumbents = incumbentsMap.getOrDefault(posId, List.of());

            List<SuccessorInfo> successors = nomsMap.getOrDefault(posId, List.of()).stream()
                    .map(nom -> {
                        List<UUID> plans = devPlansByNom.getOrDefault(nom.getId(), List.of());
                        return new SuccessorInfo(
                                nom.getId(),
                                nom.getNomineeEmployeeId(),
                                loadEmployeeName(nom.getNomineeEmployeeId()),
                                nom.getReadinessTier(),
                                nom.getRiskOfLoss(),
                                nom.getImpactOfLoss(),
                                plans
                        );
                    })
                    .toList();

            entries.add(new PositionEntry(
                    cp.getId(),
                    posId,
                    pos.code,
                    pos.title,
                    cp.getVacancyRisk(),
                    cp.getReplacementDifficulty(),
                    incumbents,
                    successors
            ));
        }

        // Coverage summary
        int noSuccessor = 0, oneReady = 0, multipleReady = 0;
        for (PositionEntry e : entries) {
            long readyNow = e.successors.stream()
                    .filter(s -> s.readiness == Readiness.READY_NOW)
                    .count();
            if (readyNow == 0) noSuccessor++;
            else if (readyNow == 1) oneReady++;
            else multipleReady++;
        }

        return new ReplacementChartResponse(entries, new CoverageSummary(noSuccessor, oneReady, multipleReady));
    }

    /** Attach a dev plan to a nomination. */
    @Transactional
    public void attachDevPlan(UUID nominationId, UUID devPlanId) {
        // Validate nomination exists
        nominations.findById(nominationId)
                .orElseThrow(() -> new ResourceNotFoundException("Nomination not found: " + nominationId));
        // Validate dev plan exists
        devPlans.findById(devPlanId)
                .orElseThrow(() -> new ResourceNotFoundException("Development plan not found: " + devPlanId));

        if (devActions.findByNominationIdAndDevPlanId(nominationId, devPlanId).isPresent()) {
            throw new BadRequestException("Dev plan already linked to this nomination");
        }

        SuccessionDevAction action = new SuccessionDevAction();
        action.setNominationId(nominationId);
        action.setDevPlanId(devPlanId);
        devActions.save(action);
    }

    /** Detach dev plan from nomination. */
    @Transactional
    public void detachDevPlan(UUID nominationId, UUID devPlanId) {
        devActions.deleteByNominationIdAndDevPlanId(nominationId, devPlanId);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private Map<UUID, PositionInfo> loadPositions(List<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        var params = new MapSqlParameterSource("ids", ids);
        return jdbc.query(
                "SELECT id::text, code, title FROM staffing.position WHERE id IN (:ids) AND tenant_id = 'default'",
                params,
                (rs, i) -> new PositionInfo(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("code"),
                        rs.getString("title")
                )
        ).stream().collect(Collectors.toMap(p -> p.id, p -> p));
    }

    private Map<UUID, List<EmployeeInfo>> loadIncumbents(List<UUID> positionIds) {
        if (positionIds.isEmpty()) return Map.of();
        var params = new MapSqlParameterSource("ids", positionIds);
        List<EmployeeInfo> incumbents = jdbc.query(
                """
                SELECT e.id::text, e.position_id::text, e.first_name, e.last_name, e.employee_no
                FROM core_hr.employee e
                WHERE e.position_id IN (:ids) AND e.employment_status = 'ACTIVE'
                """,
                params,
                (rs, i) -> new EmployeeInfo(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("first_name") + " " + rs.getString("last_name"),
                        rs.getString("employee_no")
                )
        );
        // Group by position
        Map<UUID, List<EmployeeInfo>> map = new HashMap<>();
        for (UUID posId : positionIds) {
            map.put(posId, new ArrayList<>());
        }
        for (var sql : incumbents) {
            // Re-query to get position_id per employee (simplified — already have it)
            // Actually the query returns position_id, so we need to parse it
        }
        // Simpler: query with position grouping
        return loadIncumbentsByPosition(positionIds);
    }

    private Map<UUID, List<EmployeeInfo>> loadIncumbentsByPosition(List<UUID> positionIds) {
        if (positionIds.isEmpty()) return Map.of();
        var params = new MapSqlParameterSource("ids", positionIds);
        List<IncumbentRow> rows = jdbc.query(
                """
                SELECT e.id::text AS emp_id, e.position_id::text AS pos_id,
                       e.first_name, e.last_name, e.employee_no
                FROM core_hr.employee e
                WHERE e.position_id IN (:ids) AND e.employment_status = 'ACTIVE' AND e.tenant_id = 'default'
                """,
                params,
                (rs, i) -> new IncumbentRow(
                        UUID.fromString(rs.getString("pos_id")),
                        new EmployeeInfo(
                                UUID.fromString(rs.getString("emp_id")),
                                rs.getString("first_name") + " " + rs.getString("last_name"),
                                rs.getString("employee_no")
                        )
                )
        );
        Map<UUID, List<EmployeeInfo>> map = new HashMap<>();
        for (var r : rows) {
            map.computeIfAbsent(r.positionId, k -> new ArrayList<>()).add(r.emp);
        }
        return map;
    }

    private String loadEmployeeName(UUID empId) {
        var params = new MapSqlParameterSource("id", empId);
        List<String> names = jdbc.query(
                "SELECT first_name || ' ' || last_name AS name FROM core_hr.employee WHERE id = :id AND tenant_id = 'default'",
                params,
                (rs, i) -> rs.getString("name")
        );
        return names.isEmpty() ? null : names.get(0);
    }

    private Map<UUID, List<UUID>> loadDevPlans(List<UUID> nominationIds) {
        if (nominationIds.isEmpty()) return Map.of();
        var params = new MapSqlParameterSource("ids", nominationIds);
        List<DevPlanRow> rows = jdbc.query(
                """
                SELECT nomination_id::text, dev_plan_id::text
                FROM performance.succession_dev_action
                WHERE nomination_id IN (:ids) AND tenant_id = 'default'
                """,
                params,
                (rs, i) -> new DevPlanRow(
                        UUID.fromString(rs.getString("nomination_id")),
                        UUID.fromString(rs.getString("dev_plan_id"))
                )
        );
        Map<UUID, List<UUID>> map = new HashMap<>();
        for (var r : rows) {
            map.computeIfAbsent(r.nominationId, k -> new ArrayList<>()).add(r.devPlanId);
        }
        return map;
    }

    record PositionInfo(UUID id, String code, String title) {}
    record IncumbentRow(UUID positionId, EmployeeInfo emp) {}
    record DevPlanRow(UUID nominationId, UUID devPlanId) {}

    // ── DTOs ────────────────────────────────────────────────────────────────

    public record ReplacementChartResponse(
            List<PositionEntry> positions,
            CoverageSummary coverage) {}

    public record CoverageSummary(
            int noSuccessor,
            int oneReady,
            int multipleReady) {}

    public record PositionEntry(
            UUID criticalPositionId,
            UUID positionId,
            String positionCode,
            String positionTitle,
            String vacancyRisk,
            String replacementDifficulty,
            List<EmployeeInfo> incumbents,
            List<SuccessorInfo> successors) {}

    public record EmployeeInfo(
            UUID employeeId,
            String name,
            String employeeNo) {}

    public record SuccessorInfo(
            UUID nominationId,
            UUID employeeId,
            String name,
            Readiness readiness,
            String riskOfLoss,
            String impactOfLoss,
            List<UUID> devPlanIds) {}
}
