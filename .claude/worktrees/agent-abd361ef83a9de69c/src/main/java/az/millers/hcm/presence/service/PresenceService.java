package az.millers.hcm.presence.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.presence.api.PresenceDtos.PresenceRow;
import az.millers.hcm.presence.api.PresenceDtos.PresenceSnapshot;
import az.millers.hcm.presence.domain.PresenceState;
import az.millers.hcm.presence.service.PresenceResolver.EventSnapshot;
import az.millers.hcm.presence.service.PresenceResolver.Outcome;
import az.millers.hcm.presence.service.PresenceResolver.Signals;
import az.millers.hcm.security.scope.AccessScopeService;

/**
 * M125 — assembles the "who's available right now?" snapshot.
 *
 * <p>Five bulk queries, then composes through pure-static
 * {@link PresenceResolver} on the application side. Each query is a
 * single round-trip; the per-row resolution is constant-time, so the
 * whole snapshot scales linearly with the employee count and is cheap
 * enough to refresh on a 30s SPA poll without breaking a sweat.
 */
@Service
public class PresenceService {

    /** Hard cap on the snapshot size so an HR mis-scope can't ship 50k rows over the wire. */
    public static final int MAX_ROWS = 5_000;

    private final NamedParameterJdbcTemplate jdbc;
    private final AccessScopeService accessScope;

    public PresenceService(NamedParameterJdbcTemplate jdbc,
                           AccessScopeService accessScope) {
        this.jdbc = jdbc;
        this.accessScope = accessScope;
    }

    @Transactional(readOnly = true)
    public PresenceSnapshot snapshot() {
        LocalDate today = LocalDate.now();
        Set<UUID> scope = accessScope.scopeOrNullForCurrentUser();

        // ── employees ──────────────────────────────────────────────────────
        // ACTIVE employees only; this avoids cluttering the map with people
        // who left months ago and never badge in any more.
        StringBuilder empSql = new StringBuilder(
                "SELECT id::text AS id, employee_no, first_name, last_name, "
                + "       department_name, manager_id::text AS manager_id "
                + "  FROM core_hr.employee "
                + " WHERE employment_status = 'ACTIVE'");
        MapSqlParameterSource empParams = new MapSqlParameterSource();
        if (scope != null) {
            if (scope.isEmpty()) {
                return emptySnapshot(today);
            }
            empSql.append(" AND id IN (:scopeIds)");
            empParams.addValue("scopeIds", scope);
        }
        empSql.append(" ORDER BY department_name NULLS LAST, last_name LIMIT ").append(MAX_ROWS + 1);

        List<Map<String, Object>> empRows = jdbc.queryForList(empSql.toString(), empParams);
        boolean truncated = empRows.size() > MAX_ROWS;
        if (truncated) empRows = empRows.subList(0, MAX_ROWS);

        if (empRows.isEmpty()) return emptySnapshot(today);

        List<UUID> empIds = new ArrayList<>(empRows.size());
        for (Map<String, Object> r : empRows) empIds.add(UUID.fromString((String) r.get("id")));

        MapSqlParameterSource pIds = new MapSqlParameterSource()
                .addValue("ids", empIds)
                .addValue("today", today);

        // ── approved leave overlapping today ───────────────────────────────
        // Use the M30 partial unique on (employee_id, year) is irrelevant;
        // we just want all APPROVED rows that span today.
        Set<UUID> onLeave = new HashSet<>();
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT DISTINCT employee_id::text AS id "
                + "  FROM leave_mgmt.leave_request "
                + " WHERE employee_id IN (:ids)"
                + "   AND status = 'APPROVED'"
                + "   AND :today BETWEEN start_date AND end_date",
                pIds)) {
            onLeave.add(UUID.fromString((String) r.get("id")));
        }

        // ── approved trips overlapping today ───────────────────────────────
        Set<UUID> onTrip = new HashSet<>();
        Map<UUID, String> tripDest = new HashMap<>();
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT employee_id::text AS id, destination_city "
                + "  FROM business_trip.business_trip_request "
                + " WHERE employee_id IN (:ids)"
                + "   AND status = 'APPROVED'"
                + "   AND :today BETWEEN start_date AND end_date",
                pIds)) {
            UUID id = UUID.fromString((String) r.get("id"));
            onTrip.add(id);
            String dest = (String) r.get("destination_city");
            if (dest != null && !dest.isBlank()) tripDest.put(id, dest);
        }

        // ── last attendance event today per employee ───────────────────────
        Map<UUID, EventSnapshot> lastEvent = new HashMap<>();
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT e.employee_id::text AS id, e.event_type, e.event_time "
                + "  FROM attendance.attendance_event e "
                + "  JOIN ("
                + "        SELECT employee_id, MAX(event_time) AS mx"
                + "          FROM attendance.attendance_event"
                + "         WHERE employee_id IN (:ids)"
                + "           AND event_time::date = :today"
                + "         GROUP BY employee_id"
                + "      ) m ON m.employee_id = e.employee_id AND m.mx = e.event_time"
                + " WHERE e.employee_id IN (:ids)"
                + "   AND e.event_time::date = :today",
                pIds)) {
            UUID id = UUID.fromString((String) r.get("id"));
            String et = (String) r.get("event_type");
            OffsetDateTime when = ((java.sql.Timestamp) r.get("event_time")).toInstant().atOffset(ZoneOffset.UTC);
            lastEvent.put(id, new EventSnapshot(et, when));
        }

        // ── working-day membership per employee ────────────────────────────
        // The attendance.work_schedule.work_days is a 7-char bitstring Mon..Sun.
        // We resolve each employee's active assignment for today and read the
        // bit for today's weekday. Employees with no schedule are treated as
        // working — the resolver only uses this when no attendance event
        // covers the day.
        int todayIdx = today.getDayOfWeek().getValue() - 1; // Mon=0..Sun=6
        Set<UUID> workingToday = new HashSet<>(empIds);
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT sa.employee_id::text AS id, ws.work_days "
                + "  FROM attendance.schedule_assignment sa"
                + "  JOIN attendance.work_schedule ws ON ws.id = sa.schedule_id"
                + " WHERE sa.employee_id IN (:ids)"
                + "   AND sa.effective_from <= :today"
                + "   AND (sa.effective_to IS NULL OR sa.effective_to >= :today)",
                pIds)) {
            UUID id = UUID.fromString((String) r.get("id"));
            String bits = (String) r.get("work_days");
            if (bits != null && bits.length() > todayIdx && bits.charAt(todayIdx) == '0') {
                workingToday.remove(id);
            }
        }

        // ── compose ────────────────────────────────────────────────────────
        List<PresenceRow> rows = new ArrayList<>(empRows.size());
        List<Outcome> outcomes = new ArrayList<>(empRows.size());
        for (Map<String, Object> e : empRows) {
            UUID id = UUID.fromString((String) e.get("id"));
            Signals s = new Signals(
                    lastEvent.get(id),
                    onLeave.contains(id),
                    onTrip.contains(id),
                    workingToday.contains(id));
            Outcome o = PresenceResolver.resolve(s);
            outcomes.add(o);
            String first = (String) e.get("first_name");
            String last = (String) e.get("last_name");
            String name = (first == null ? "" : first) + " " + (last == null ? "" : last);
            String mgr = (String) e.get("manager_id");
            String note = noteFor(o.state(), tripDest.get(id));
            rows.add(new PresenceRow(
                    id,
                    (String) e.get("employee_no"),
                    name.trim(),
                    (String) e.get("department_name"),
                    mgr == null ? null : UUID.fromString(mgr),
                    o.state(),
                    o.since(),
                    note));
        }
        Map<PresenceState, Long> counts = PresenceResolver.counts(outcomes);
        return new PresenceSnapshot(
                today,
                OffsetDateTime.now(),
                rows.size(),
                counts,
                rows);
    }

    private static String noteFor(PresenceState s, String tripDestination) {
        if (s == PresenceState.ON_TRIP && tripDestination != null) return tripDestination;
        return null;
    }

    private PresenceSnapshot emptySnapshot(LocalDate today) {
        Map<PresenceState, Long> empty = PresenceResolver.counts(Collections.emptyList());
        return new PresenceSnapshot(today, OffsetDateTime.now(), 0, empty, List.of());
    }
}
