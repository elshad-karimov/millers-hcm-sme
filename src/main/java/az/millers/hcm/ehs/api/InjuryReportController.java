package az.millers.hcm.ehs.api;
import az.millers.hcm.common.tenant.TenantContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import az.millers.hcm.ehs.api.dto.InjuryReportDtos.*;
import az.millers.hcm.ehs.domain.InjuryReport;
import az.millers.hcm.ehs.service.InjuryReportService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M449 — EHS injury report management.
 * MEDICAL CONFIDENTIALITY: all reads restricted to HR_ADMIN only.
 */
@RestController
@RequestMapping("/api/ehs/injuries")
public class InjuryReportController {


    private final InjuryReportService service;
    private final NamedParameterJdbcTemplate jdbc;

    public InjuryReportController(InjuryReportService service,
                                  NamedParameterJdbcTemplate jdbc) {
        this.service = service;
        this.jdbc = jdbc;
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public InjuryReportResponse create(@RequestBody CreateInjuryReportRequest req) {
        InjuryReport report = service.create(
                req.incidentId(),
                req.employeeId(),
                req.injuryType(),
                req.bodyPart(),
                req.severity(),
                req.medicalTreatment(),
                req.firstAid(),
                req.hospital(),
                req.lostTimeDays(),
                req.restrictedDuty(),
                req.insuranceClaimRef(),
                req.notes()
        );

        return toResponse(report);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public InjuryReportResponse update(@PathVariable UUID id,
                                        @RequestBody UpdateInjuryReportRequest req) {
        InjuryReport report = service.update(
                id,
                req.injuryType(),
                req.bodyPart(),
                req.severity(),
                req.medicalTreatment(),
                req.firstAid(),
                req.hospital(),
                req.lostTimeDays(),
                req.restrictedDuty(),
                req.insuranceClaimRef(),
                req.notes()
        );

        return toResponse(report);
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY) // MEDICAL CONFIDENTIALITY: HR_ADMIN only
    public InjuryReportResponse get(@PathVariable UUID id) {
        InjuryReport report = service.get(id);
        return toResponse(report);
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY) // MEDICAL CONFIDENTIALITY: HR_ADMIN only
    public List<InjuryReportResponse> list(
            @RequestParam(required = false) UUID incidentId,
            @RequestParam(required = false) UUID employeeId) {

        List<InjuryReport> reports = service.list(incidentId, employeeId);

        // Batch fetch employee names
        List<UUID> empIds = reports.stream()
                .map(InjuryReport::getEmployeeId)
                .distinct()
                .toList();

        Map<UUID, String> names = fetchEmployeeNames(empIds);

        return reports.stream()
                .map(r -> InjuryReportResponse.from(r, names.get(r.getEmployeeId())))
                .collect(Collectors.toList());
    }

    // ── HELPERS ──────────────────────────────────────────────────────────────

    private InjuryReportResponse toResponse(InjuryReport report) {
        String employeeName = fetchEmployeeName(report.getEmployeeId());
        return InjuryReportResponse.from(report, employeeName);
    }

    private String fetchEmployeeName(UUID employeeId) {
        if (employeeId == null) return null;
        return jdbc.queryForObject(
                "SELECT first_name || ' ' || last_name FROM core_hr.employee WHERE id = :id AND tenant_id = :tenant",
                Map.of("id", employeeId, "tenant", TenantContext.current()),
                String.class
        );
    }

    private Map<UUID, String> fetchEmployeeNames(List<UUID> employeeIds) {
        if (employeeIds == null || employeeIds.isEmpty()) return Map.of();
        return jdbc.query(
                "SELECT id, first_name || ' ' || last_name AS name FROM core_hr.employee WHERE id IN (:ids) AND tenant_id = :tenant",
                Map.of("ids", employeeIds, "tenant", TenantContext.current()),
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
