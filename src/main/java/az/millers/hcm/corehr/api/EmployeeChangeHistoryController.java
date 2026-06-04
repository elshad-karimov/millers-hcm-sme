package az.millers.hcm.corehr.api;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.corehr.api.dto.ChangeHistoryDtos.EmployeeChangeHistory;
import az.millers.hcm.corehr.service.EmployeeChangeHistoryService;
import az.millers.hcm.security.SecurityRoles;
import az.millers.hcm.selfservice.service.EmployeeContextService;

/**
 * Per-employee unified change-history timeline (M117).
 *
 * <ul>
 *   <li>{@code GET /api/employees/{id}/change-history} — HR_PLUS_MANAGERS read;
 *       the service additionally applies the ABAC scope so a manager
 *       only sees their direct reports.</li>
 *   <li>{@code GET /api/me/change-history} — every authenticated employee can
 *       see their own timeline.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
public class EmployeeChangeHistoryController {

    private final EmployeeChangeHistoryService service;
    private final EmployeeContextService context;

    public EmployeeChangeHistoryController(EmployeeChangeHistoryService service,
                                       EmployeeContextService context) {
        this.service = service;
        this.context = context;
    }

    @GetMapping("/employees/{id}/change-history")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public EmployeeChangeHistory forEmployee(@PathVariable("id") UUID employeeId) {
        return service.timelineFor(employeeId);
    }

    @GetMapping("/me/change-history")
    @PreAuthorize("isAuthenticated()")
    public EmployeeChangeHistory mine() {
        UUID empId = context.currentEmployee().getId();
        return service.timelineFor(empId);
    }
}
