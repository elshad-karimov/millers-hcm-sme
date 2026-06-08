package az.millers.hcm.reporting.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.reporting.api.dto.OrgReportDtos.HeadcountReport;
import az.millers.hcm.reporting.api.dto.OrgReportDtos.HrbpCoverageReport;
import az.millers.hcm.reporting.api.dto.OrgReportDtos.OrgDistributionReport;
import az.millers.hcm.reporting.api.dto.OrgReportDtos.OrgFlatReport;
import az.millers.hcm.reporting.service.OrgNativeReportService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M145 — org-native report endpoints (§35).
 *
 * <p>All reports are derived from the currently ACTIVE structure version.
 * Returns empty result shapes (not 404) when no active version exists.
 *
 * <p>Read access: HR roles + managers (same gate as span-of-control).
 */
@RestController
@RequestMapping("/api/reports/org")
public class OrgNativeReportController {

    private final OrgNativeReportService service;

    public OrgNativeReportController(OrgNativeReportService service) {
        this.service = service;
    }

    /** Headcount budget vs actual for every org unit in the active version. */
    @GetMapping("/headcount")
    @PreAuthorize(SecurityRoles.READ_REPORTS)
    public HeadcountReport headcount() {
        return service.headcount();
    }

    /** HRBP assignment coverage across all units in the active version. */
    @GetMapping("/hrbp-coverage")
    @PreAuthorize(SecurityRoles.READ_REPORTS)
    public HrbpCoverageReport hrbpCoverage() {
        return service.hrbpCoverage();
    }

    /** Unit count grouped by lifecycle state and unit type. */
    @GetMapping("/distribution")
    @PreAuthorize(SecurityRoles.READ_REPORTS)
    public OrgDistributionReport distribution() {
        return service.distribution();
    }

    /** Flat list of all units with all attributes (useful as a CSV export source). */
    @GetMapping("/flat")
    @PreAuthorize(SecurityRoles.READ_REPORTS)
    public OrgFlatReport flat() {
        return service.flatExport();
    }
}
