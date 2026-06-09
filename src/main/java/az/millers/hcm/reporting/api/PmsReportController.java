package az.millers.hcm.reporting.api;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.reporting.api.dto.PmsReportDtos.DepartmentKpiReport;
import az.millers.hcm.reporting.api.dto.PmsReportDtos.GoalCompletionReport;
import az.millers.hcm.reporting.api.dto.PmsReportDtos.PerformerReport;
import az.millers.hcm.reporting.service.PmsReportService;
import az.millers.hcm.security.SecurityRoles;

/**
 * PMS report endpoints (M226 / PRD §8.13.11).
 *
 * <p>Base path: {@code /api/reports/performance}
 */
@RestController
@RequestMapping("/api/reports/performance")
public class PmsReportController {

    private static final BigDecimal DEFAULT_HIGH_THRESHOLD = new BigDecimal("4.0");
    private static final BigDecimal DEFAULT_LOW_THRESHOLD  = new BigDecimal("2.0");

    private final PmsReportService service;

    public PmsReportController(PmsReportService service) {
        this.service = service;
    }

    @GetMapping("/department-kpi")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public DepartmentKpiReport departmentKpi(@RequestParam UUID cycleId) {
        return service.departmentKpi(cycleId);
    }

    @GetMapping("/goal-completion")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public GoalCompletionReport goalCompletion(@RequestParam UUID cycleId) {
        return service.goalCompletion(cycleId);
    }

    /** High performers: employees whose final_rating >= minRating (default 4.0). */
    @GetMapping("/high-performers")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public PerformerReport highPerformers(
            @RequestParam UUID cycleId,
            @RequestParam(required = false) BigDecimal minRating) {
        BigDecimal threshold = minRating != null ? minRating : DEFAULT_HIGH_THRESHOLD;
        return service.highPerformers(cycleId, threshold);
    }

    /** Low performers: employees whose final_rating <= maxRating (default 2.0). */
    @GetMapping("/low-performers")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public PerformerReport lowPerformers(
            @RequestParam UUID cycleId,
            @RequestParam(required = false) BigDecimal maxRating) {
        BigDecimal threshold = maxRating != null ? maxRating : DEFAULT_LOW_THRESHOLD;
        return service.lowPerformers(cycleId, threshold);
    }
}
