package az.millers.hcm.ehs.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import az.millers.hcm.ehs.api.dto.PpeDtos.*;
import az.millers.hcm.ehs.domain.PpeAssignment;
import az.millers.hcm.ehs.domain.PpeItem;
import az.millers.hcm.ehs.service.PpeService;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.SecurityRoles;
import az.millers.hcm.selfservice.service.EmployeeContextService;

/**
 * M451 — PPE management.
 * Employee can see own via /api/self/ehs/ppe-assignments (AccessScopeService guard on employeeId param).
 */
@RestController
public class PpeController {

    private static final String TENANT = "default";

    private final PpeService service;
    private final NamedParameterJdbcTemplate jdbc;
    private final EmployeeContextService employeeContext;
    private final CurrentRequest currentRequest;

    public PpeController(PpeService service,
                         NamedParameterJdbcTemplate jdbc,
                         EmployeeContextService employeeContext,
                         CurrentRequest currentRequest) {
        this.service = service;
        this.jdbc = jdbc;
        this.employeeContext = employeeContext;
        this.currentRequest = currentRequest;
    }

    // ── HR Endpoints ─────────────────────────────────────────────────────────

    @PostMapping("/api/ehs/ppe-assignments")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public PpeAssignmentResponse issue(@RequestBody IssuePpeRequest req) {
        PpeAssignment assignment = service.issue(
                req.employeeId(),
                req.ppeItemId(),
                req.issuedAt(),
                req.expiryDate(),
                req.conditionAtIssue(),
                req.notes()
        );

        return toAssignmentResponse(assignment);
    }

    @PostMapping("/api/ehs/ppe-assignments/{id}/return")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public PpeAssignmentResponse returnPpe(@PathVariable UUID id,
                                            @RequestBody ReturnPpeRequest req) {
        PpeAssignment assignment = service.returnPpe(id, req.returnedAt(), req.conditionAtReturn());
        return toAssignmentResponse(assignment);
    }

    @GetMapping("/api/ehs/ppe-assignments/{id}")
    @PreAuthorize(SecurityRoles.READ_HR)
    public PpeAssignmentResponse getAssignment(@PathVariable UUID id) {
        PpeAssignment assignment = service.getAssignment(id);
        return toAssignmentResponse(assignment);
    }

    @GetMapping("/api/ehs/ppe-assignments")
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<PpeAssignmentResponse> listAssignments(
            @RequestParam(required = false) UUID employeeId) {

        List<PpeAssignment> assignments = service.listAssignments(employeeId);
        return toAssignmentResponses(assignments);
    }

    @GetMapping("/api/ehs/ppe-assignments/expiring")
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<PpeAssignmentResponse> expiringAssignments() {
        List<PpeAssignment> assignments = service.findExpiringAssignments();
        return toAssignmentResponses(assignments);
    }

    @GetMapping("/api/ehs/ppe-items")
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<PpeItemResponse> listItems(
            @RequestParam(required = false) Boolean activeOnly) {

        return service.listItems(activeOnly).stream()
                .map(PpeItemResponse::from)
                .collect(Collectors.toList());
    }

    @GetMapping("/api/ehs/ppe-items/{id}")
    @PreAuthorize(SecurityRoles.READ_HR)
    public PpeItemResponse getItem(@PathVariable UUID id) {
        PpeItem item = service.getItem(id);
        return PpeItemResponse.from(item);
    }

    // ── Self-Service Endpoint ────────────────────────────────────────────────

    @GetMapping("/api/self/ehs/ppe-assignments")
    @PreAuthorize("isAuthenticated()")
    public List<PpeAssignmentResponse> getSelfPpeAssignments() {
        UUID employeeId = employeeContext.currentEmployee().getId();
        List<PpeAssignment> assignments = service.listAssignments(employeeId);
        return toAssignmentResponses(assignments);
    }

    // ── HELPERS ──────────────────────────────────────────────────────────────

    private PpeAssignmentResponse toAssignmentResponse(PpeAssignment assignment) {
        String employeeName = fetchEmployeeName(assignment.getEmployeeId());
        String itemName = fetchPpeItemName(assignment.getPpeItemId());

        return new PpeAssignmentResponse(
                assignment.getId(),
                assignment.getTenantId(),
                assignment.getEmployeeId(),
                employeeName,
                assignment.getPpeItemId(),
                itemName,
                assignment.getIssuedAt(),
                assignment.getExpiryDate(),
                assignment.getReturnedAt(),
                assignment.getConditionAtIssue(),
                assignment.getConditionAtReturn(),
                assignment.getNotes(),
                assignment.getCreatedBy(),
                assignment.getCreatedAt(),
                assignment.getUpdatedBy(),
                assignment.getUpdatedAt()
        );
    }

    private List<PpeAssignmentResponse> toAssignmentResponses(List<PpeAssignment> assignments) {
        // Batch fetch employee and item names
        List<UUID> empIds = assignments.stream().map(PpeAssignment::getEmployeeId).distinct().toList();
        List<UUID> itemIds = assignments.stream().map(PpeAssignment::getPpeItemId).distinct().toList();

        Map<UUID, String> empNames = fetchEmployeeNames(empIds);
        Map<UUID, String> itemNames = fetchPpeItemNames(itemIds);

        return assignments.stream()
                .map(a -> new PpeAssignmentResponse(
                        a.getId(),
                        a.getTenantId(),
                        a.getEmployeeId(),
                        empNames.get(a.getEmployeeId()),
                        a.getPpeItemId(),
                        itemNames.get(a.getPpeItemId()),
                        a.getIssuedAt(),
                        a.getExpiryDate(),
                        a.getReturnedAt(),
                        a.getConditionAtIssue(),
                        a.getConditionAtReturn(),
                        a.getNotes(),
                        a.getCreatedBy(),
                        a.getCreatedAt(),
                        a.getUpdatedBy(),
                        a.getUpdatedAt()
                ))
                .collect(Collectors.toList());
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

    private String fetchPpeItemName(UUID itemId) {
        if (itemId == null) return null;
        return jdbc.queryForObject(
                "SELECT name FROM ehs.ppe_item WHERE id = :id AND tenant_id = :tenant",
                Map.of("id", itemId, "tenant", TENANT),
                String.class
        );
    }

    private Map<UUID, String> fetchPpeItemNames(List<UUID> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) return Map.of();
        return jdbc.query(
                "SELECT id, name FROM ehs.ppe_item WHERE id IN (:ids) AND tenant_id = :tenant",
                Map.of("ids", itemIds, "tenant", TENANT),
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
