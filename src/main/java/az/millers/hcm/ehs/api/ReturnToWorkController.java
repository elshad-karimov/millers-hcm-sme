package az.millers.hcm.ehs.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import az.millers.hcm.ehs.api.dto.ReturnToWorkDtos.*;
import az.millers.hcm.ehs.domain.ReturnToWorkPlan;
import az.millers.hcm.ehs.domain.ReturnToWorkStatus;
import az.millers.hcm.ehs.service.ReturnToWorkService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M449 — Return-to-work plan management.
 * MEDICAL CONFIDENTIALITY: reads restricted to HR_ADMIN only.
 * Manager approval: employee's manager can approve (checked in service).
 * HR approval: HR_ADMIN only.
 */
@RestController
@RequestMapping("/api/ehs/return-to-work")
public class ReturnToWorkController {

    private static final String TENANT = "default";

    private final ReturnToWorkService service;
    private final NamedParameterJdbcTemplate jdbc;

    public ReturnToWorkController(ReturnToWorkService service,
                                   NamedParameterJdbcTemplate jdbc) {
        this.service = service;
        this.jdbc = jdbc;
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public ReturnToWorkResponse create(@RequestBody CreateReturnToWorkRequest req) {
        ReturnToWorkPlan plan = service.create(
                req.injuryReportId(),
                req.employeeId(),
                req.medicalClearanceDate(),
                req.restrictions(),
                req.modifiedSchedule()
        );

        return toResponse(plan);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public ReturnToWorkResponse update(@PathVariable UUID id,
                                        @RequestBody UpdateReturnToWorkRequest req) {
        ReturnToWorkPlan plan = service.update(
                id,
                req.medicalClearanceDate(),
                req.restrictions(),
                req.modifiedSchedule(),
                req.status()
        );

        return toResponse(plan);
    }

    @PostMapping("/{id}/manager-approval")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS) // Manager can approve
    public ReturnToWorkResponse setManagerApproval(@PathVariable UUID id,
                                                     @RequestBody SetApprovalRequest req) {
        service.setManagerApproval(id, req.approved());
        ReturnToWorkPlan plan = service.get(id);
        return toResponse(plan);
    }

    @PostMapping("/{id}/hr-approval")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public ReturnToWorkResponse setHrApproval(@PathVariable UUID id,
                                               @RequestBody SetApprovalRequest req) {
        service.setHrApproval(id, req.approved());
        ReturnToWorkPlan plan = service.get(id);
        return toResponse(plan);
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS) // HR_ADMIN gets it via READ_HR subset
    public ReturnToWorkResponse get(@PathVariable UUID id) {
        ReturnToWorkPlan plan = service.get(id);
        return toResponse(plan);
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS) // HR_ADMIN gets it via READ_HR subset
    public List<ReturnToWorkResponse> list(
            @RequestParam(required = false) ReturnToWorkStatus status,
            @RequestParam(required = false) UUID employeeId) {

        List<ReturnToWorkPlan> plans = service.list(status, employeeId);

        // Batch fetch employee names
        List<UUID> empIds = plans.stream()
                .map(ReturnToWorkPlan::getEmployeeId)
                .distinct()
                .toList();

        Map<UUID, String> names = fetchEmployeeNames(empIds);

        return plans.stream()
                .map(p -> ReturnToWorkResponse.from(p, names.get(p.getEmployeeId())))
                .collect(Collectors.toList());
    }

    // ── HELPERS ──────────────────────────────────────────────────────────────

    private ReturnToWorkResponse toResponse(ReturnToWorkPlan plan) {
        String employeeName = fetchEmployeeName(plan.getEmployeeId());
        return ReturnToWorkResponse.from(plan, employeeName);
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
