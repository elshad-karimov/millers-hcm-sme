package az.millers.hcm.analytics;

import az.millers.hcm.security.SecurityRoles;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Warehouse analytics REST API — M58 (PRD Could-Have).
 * All endpoints require HR_ADMIN or SYSTEM_ADMIN.
 */
@RestController
@RequestMapping("/api/analytics/warehouse")
@PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
public class WarehouseController {

    private final WarehouseEtlService etl;
    private final WarehouseAnalyticsService analytics;

    public WarehouseController(WarehouseEtlService etl, WarehouseAnalyticsService analytics) {
        this.etl = etl;
        this.analytics = analytics;
    }

    /** Trigger an on-demand ETL sync from PostgreSQL to ClickHouse. */
    @PostMapping("/sync")
    public SyncResult sync() {
        return etl.sync();
    }

    /** Check warehouse availability. */
    @GetMapping("/status")
    public Map<String, Object> status() {
        boolean up = analytics.isAvailable();
        return Map.of("available", up, "engine", "ClickHouse");
    }

    /** Current headcount grouped by department. */
    @GetMapping("/headcount-by-dept")
    public List<Map<String, Object>> headcountByDept() {
        return analytics.headcountByDept();
    }

    /** Daily attendance trend (last 60 days). */
    @GetMapping("/attendance-trend")
    public List<Map<String, Object>> attendanceTrend() {
        return analytics.attendanceTrend();
    }

    /** Monthly payroll cost trend. */
    @GetMapping("/payroll-trend")
    public List<Map<String, Object>> payrollTrend() {
        return analytics.payrollTrend();
    }

    /** Leave utilisation summary by type. */
    @GetMapping("/leave-summary")
    public List<Map<String, Object>> leaveSummary() {
        return analytics.leaveSummary();
    }
}
