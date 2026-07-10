package az.millers.hcm.payroll.api;

import az.millers.hcm.payroll.service.LaborCostReportService;
import az.millers.hcm.security.SecurityRoles;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * M486: Labor cost report endpoints (Finance/HR confidential).
 */
@RestController
@RequestMapping("/api/reports/labor-cost")
public class LaborCostReportController {

    private final LaborCostReportService service;

    public LaborCostReportController(LaborCostReportService service) {
        this.service = service;
    }

    @GetMapping("/by-project")
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public List<Map<String, Object>> byProject(@RequestParam int year, @RequestParam int month) {
        return service.byProject(year, month);
    }

    @GetMapping("/by-department")
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public List<Map<String, Object>> byDepartment(@RequestParam int year, @RequestParam int month) {
        return service.byDepartment(year, month);
    }

    @GetMapping("/monthly-summary")
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public List<Map<String, Object>> monthlySummary(@RequestParam int year, @RequestParam int month) {
        return service.monthlySummary(year, month);
    }
}
