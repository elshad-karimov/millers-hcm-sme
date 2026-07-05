package az.millers.hcm.ehs.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import az.millers.hcm.ehs.api.dto.IncidentDtos.*;
import az.millers.hcm.ehs.domain.*;
import az.millers.hcm.ehs.service.IncidentService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M448 — EHS incident management.
 */
@RestController
@RequestMapping("/api/ehs/incidents")
public class IncidentController {

    private static final String TENANT = "default";

    private final IncidentService service;
    private final NamedParameterJdbcTemplate jdbc;

    public IncidentController(IncidentService service,
                              NamedParameterJdbcTemplate jdbc) {
        this.service = service;
        this.jdbc = jdbc;
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public IncidentResponse report(@RequestBody ReportIncidentRequest req) {
        List<IncidentService.WitnessInput> witnesses = req.witnesses() != null
                ? req.witnesses().stream()
                .map(w -> new IncidentService.WitnessInput(w.name(), w.contact(), w.statement()))
                .toList()
                : null;

        Incident incident = service.report(
                req.incidentDate(),
                req.incidentTime(),
                req.workLocationId(),
                req.orgUnitId(),
                req.incidentType(),
                req.severity(),
                req.description(),
                req.immediateAction(),
                req.involvedEmployeeIds(),
                witnesses
        );

        return toResponse(incident);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public IncidentResponse update(@PathVariable UUID id,
                                    @RequestBody UpdateIncidentRequest req) {
        List<IncidentService.WitnessInput> witnesses = req.witnesses() != null
                ? req.witnesses().stream()
                .map(w -> new IncidentService.WitnessInput(w.name(), w.contact(), w.statement()))
                .toList()
                : null;

        Incident incident = service.update(
                id,
                req.description(),
                req.immediateAction(),
                req.status(),
                req.involvedEmployeeIds(),
                witnesses
        );

        return toResponse(incident);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public IncidentResponse close(@PathVariable UUID id,
                                   @RequestBody CloseIncidentRequest req) {
        service.close(id, req.resolution());
        Incident incident = service.get(id);
        return toResponse(incident);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public IncidentResponse get(@PathVariable UUID id) {
        Incident incident = service.get(id);
        return toResponse(incident);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<IncidentResponse> list(
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) UUID reportedByEmployeeId) {

        List<Incident> incidents = service.list(status, reportedByEmployeeId);

        // Batch fetch reporter names
        List<UUID> reporterIds = incidents.stream()
                .map(Incident::getReportedByEmployeeId)
                .distinct()
                .toList();

        Map<UUID, String> reporterNames = fetchEmployeeNames(reporterIds);

        return incidents.stream()
                .map(i -> IncidentResponse.from(i, reporterNames.get(i.getReportedByEmployeeId())))
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}/involved")
    @PreAuthorize("isAuthenticated()")
    public List<IncidentInvolvedResponse> getInvolved(@PathVariable UUID id) {
        List<IncidentInvolved> involved = service.getInvolved(id);

        List<UUID> empIds = involved.stream().map(IncidentInvolved::getEmployeeId).distinct().toList();
        Map<UUID, String> names = fetchEmployeeNames(empIds);

        return involved.stream()
                .map(inv -> new IncidentInvolvedResponse(
                        inv.getId(),
                        inv.getIncidentId(),
                        inv.getEmployeeId(),
                        names.get(inv.getEmployeeId())
                ))
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}/witnesses")
    @PreAuthorize("isAuthenticated()")
    public List<IncidentWitnessResponse> getWitnesses(@PathVariable UUID id) {
        return service.getWitnesses(id).stream()
                .map(IncidentWitnessResponse::from)
                .collect(Collectors.toList());
    }

    // ── HELPERS ──────────────────────────────────────────────────────────────

    private IncidentResponse toResponse(Incident incident) {
        String reporterName = fetchEmployeeName(incident.getReportedByEmployeeId());
        return IncidentResponse.from(incident, reporterName);
    }

    private String fetchEmployeeName(UUID employeeId) {
        if (employeeId == null) return null;
        return jdbc.queryForObject(
                "SELECT first_name || ' ' || last_name FROM core_hr.employee WHERE id = :id AND tenant_id = :tenant",
                Map.of("id", employeeId, "tenant", TENANT),
                String.class
        );
    }

    private Map<UUID, String> fetchEmployeeNames(List<UUID> employeeIds) {
        if (employeeIds == null || employeeIds.isEmpty()) return Map.of();
        return jdbc.query(
                "SELECT id, first_name || ' ' || last_name AS name FROM core_hr.employee WHERE id IN (:ids) AND tenant_id = :tenant",
                Map.of("ids", employeeIds, "tenant", TENANT),
                rs -> {
                    Map<UUID, String> map = new java.util.HashMap<>();
                    while (rs.next()) {
                        map.put(UUID.fromString(rs.getString("id")), rs.getString("name"));
                    }
                    return map;
                }
        );
    }
}
