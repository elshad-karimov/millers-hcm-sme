package az.millers.hcm.leave.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.leave.api.dto.TeamCalendarDtos.DailyRollup;
import az.millers.hcm.leave.api.dto.TeamCalendarDtos.TeamCalendarResponse;
import az.millers.hcm.leave.api.dto.TeamCalendarDtos.TeamLeaveEntry;
import az.millers.hcm.leave.domain.LeaveRequest;
import az.millers.hcm.leave.repo.LeaveRequestRepository;
import az.millers.hcm.leave.service.LeaveOverlapAnalyzer.LeaveInterval;
import az.millers.hcm.organization.repo.OrgUnitRepository;
import az.millers.hcm.security.scope.AccessScopeService;

/**
 * M131 — team time-off calendar. One API call returns a month's worth
 * of approved + pending leave for a chosen org unit (intersected with
 * the caller's ABAC scope), plus per-day concurrent-absence rollups.
 *
 * <p>Math lives in {@link LeaveOverlapAnalyzer} — this service is
 * thin orchestration: resolve scope → load → compose → respond.
 */
@Service
public class TeamCalendarService {

    /**
     * Hard cap on team size. A manager-and-up calendar over 5000+
     * employees would overwhelm the SPA; reject up-front rather than
     * choke the request thread.
     */
    public static final int MAX_TEAM_SIZE = 1000;

    /** Default threshold for "too many out at once" — overridden by SPA. */
    public static final BigDecimal DEFAULT_THRESHOLD_PERCENT = BigDecimal.valueOf(40);

    private final LeaveRequestRepository requests;
    private final EmployeeRepository employees;
    private final OrgUnitRepository orgUnits;
    private final AccessScopeService scope;
    private final NamedParameterJdbcTemplate jdbc;

    public TeamCalendarService(LeaveRequestRepository requests,
                                EmployeeRepository employees,
                                OrgUnitRepository orgUnits,
                                AccessScopeService scope,
                                NamedParameterJdbcTemplate jdbc) {
        this.requests = requests;
        this.employees = employees;
        this.orgUnits = orgUnits;
        this.scope = scope;
        this.jdbc = jdbc;
    }

    /**
     * Returns the calendar for {@code [windowStart, windowEnd]} restricted
     * to {@code orgUnitId} (and its descendants). When {@code orgUnitId}
     * is null, falls back to the caller's scope (manager view → reports,
     * HR_ADMIN → everyone the caller can see).
     */
    @Transactional(readOnly = true)
    public TeamCalendarResponse teamCalendar(UUID orgUnitId,
                                              LocalDate windowStart,
                                              LocalDate windowEnd,
                                              BigDecimal thresholdPercent) {
        if (windowStart == null || windowEnd == null) {
            throw new BadRequestException("windowStart and windowEnd are required");
        }
        if (windowEnd.isBefore(windowStart)) {
            throw new BadRequestException("windowEnd must be on or after windowStart");
        }
        if (windowStart.plusDays(95).isBefore(windowEnd)) {
            throw new BadRequestException("Window cannot exceed 95 days");
        }
        BigDecimal threshold = thresholdPercent != null
                ? thresholdPercent
                : DEFAULT_THRESHOLD_PERCENT;

        // 1. Build the team set: orgUnit (if any) intersected with caller scope.
        Set<UUID> team = resolveTeam(orgUnitId);
        if (team.isEmpty()) {
            return new TeamCalendarResponse(windowStart, windowEnd, orgUnitId,
                    0, threshold, List.of(), List.of());
        }
        if (team.size() > MAX_TEAM_SIZE) {
            throw new BadRequestException(
                    "Team size " + team.size() + " exceeds the " + MAX_TEAM_SIZE + " cap; narrow the org unit");
        }

        // 2. Load leave rows.
        List<LeaveRequest> rows = requests.findActiveForTeamInRange(team, windowStart, windowEnd);

        // 3. Hydrate employee + leave-type lookups in two bulk queries.
        Map<UUID, EmployeeRow> empById = loadEmployeeRows(extractIds(rows, LeaveRequest::getEmployeeId));
        Map<UUID, LeaveTypeRow> typeById = loadLeaveTypeRows(extractIds(rows, LeaveRequest::getLeaveTypeId));

        // 4. Build wire entries + interval views.
        List<TeamLeaveEntry> entries = new ArrayList<>(rows.size());
        List<LeaveInterval> intervals = new ArrayList<>(rows.size());
        for (LeaveRequest r : rows) {
            EmployeeRow e = empById.get(r.getEmployeeId());
            LeaveTypeRow t = typeById.get(r.getLeaveTypeId());
            entries.add(new TeamLeaveEntry(
                    r.getId(),
                    r.getEmployeeId(),
                    e == null ? null : e.employeeNo,
                    e == null ? null : (e.firstName + " " + e.lastName),
                    r.getLeaveTypeId(),
                    t == null ? null : t.name,
                    t == null ? null : t.color,
                    r.getStartDate(),
                    r.getEndDate(),
                    r.getTotalDays(),
                    r.isHalfDay(),
                    r.getStatus()));
            intervals.add(new LeaveInterval(
                    r.getEmployeeId(), r.getStartDate(), r.getEndDate(),
                    r.getStatus().name().equals("APPROVED")));
        }

        // 5. Daily rollup via the pure-static analyzer.
        Map<LocalDate, Integer> counts =
                LeaveOverlapAnalyzer.dailyCounts(intervals, windowStart, windowEnd);
        Set<LocalDate> flagged = new HashSet<>(
                LeaveOverlapAnalyzer.flagDays(counts, team.size(), threshold));
        List<DailyRollup> days = new ArrayList<>(counts.size());
        for (var e : counts.entrySet()) {
            days.add(new DailyRollup(
                    e.getKey(),
                    e.getValue(),
                    LeaveOverlapAnalyzer.percentOff(e.getValue(), team.size()),
                    flagged.contains(e.getKey())));
        }

        return new TeamCalendarResponse(
                windowStart, windowEnd, orgUnitId, team.size(), threshold, entries, days);
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private Set<UUID> resolveTeam(UUID orgUnitId) {
        Set<UUID> myScope = scope.scopeOrNullForCurrentUser(); // null = unrestricted
        Set<UUID> orgUnitTeam = null;
        if (orgUnitId != null) {
            List<UUID> unitIds = orgUnits.descendantUnitIds(orgUnitId);
            if (unitIds.isEmpty()) return Set.of();
            orgUnitTeam = new LinkedHashSet<>(employees.findIdsByOrgUnitIdIn(unitIds));
        }
        if (orgUnitTeam == null && myScope == null) {
            // Unrestricted, no org filter → load every active employee id.
            // The MAX_TEAM_SIZE guard catches the case where this would be
            // too many to reasonably display.
            return new HashSet<>(allActiveEmployeeIds());
        }
        if (orgUnitTeam == null) return new HashSet<>(myScope);
        if (myScope == null) return orgUnitTeam;
        // Intersect.
        orgUnitTeam.retainAll(myScope);
        return orgUnitTeam;
    }

    private List<UUID> allActiveEmployeeIds() {
        return jdbc.queryForList(
                "SELECT id::text AS id FROM core_hr.employee "
                + "WHERE employment_status IS NULL OR employment_status <> 'TERMINATED'",
                new MapSqlParameterSource(), String.class)
                .stream().map(UUID::fromString).toList();
    }

    private static <T> Set<UUID> extractIds(List<LeaveRequest> rows, java.util.function.Function<LeaveRequest, UUID> f) {
        Set<UUID> out = new HashSet<>();
        for (LeaveRequest r : rows) {
            UUID id = f.apply(r);
            if (id != null) out.add(id);
        }
        return out;
    }

    private record EmployeeRow(String employeeNo, String firstName, String lastName) {}
    private record LeaveTypeRow(String name, String color) {}

    private Map<UUID, EmployeeRow> loadEmployeeRows(Set<UUID> ids) {
        if (ids.isEmpty()) return Collections.emptyMap();
        Map<UUID, EmployeeRow> out = new HashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT id::text AS id, employee_no, first_name, last_name "
                + "  FROM core_hr.employee WHERE id IN (:ids)",
                new MapSqlParameterSource("ids", ids))) {
            out.put(UUID.fromString((String) row.get("id")),
                    new EmployeeRow(
                            (String) row.get("employee_no"),
                            (String) row.get("first_name"),
                            (String) row.get("last_name")));
        }
        return out;
    }

    private Map<UUID, LeaveTypeRow> loadLeaveTypeRows(Set<UUID> ids) {
        if (ids.isEmpty()) return Collections.emptyMap();
        Map<UUID, LeaveTypeRow> out = new HashMap<>();
        // leave_type table doesn't currently store a color — best-effort
        // map from leave-type code to a stable palette so the SPA paints
        // consistently across reloads.
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT id::text AS id, name, code "
                + "  FROM leave_mgmt.leave_type WHERE id IN (:ids)",
                new MapSqlParameterSource("ids", ids))) {
            out.put(UUID.fromString((String) row.get("id")),
                    new LeaveTypeRow(
                            (String) row.get("name"),
                            paletteFor((String) row.get("code"))));
        }
        return out;
    }

    private static String paletteFor(String code) {
        if (code == null) return "#8884d8";
        // Stable per-code colour — used by SPA chips. Five hand-picked
        // hues map deterministically by code hash.
        String[] palette = { "#1677ff", "#52c41a", "#fa8c16", "#722ed1", "#13c2c2" };
        int idx = Math.abs(code.hashCode()) % palette.length;
        return palette[idx];
    }
}
