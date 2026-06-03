package az.millers.hcm.recruitment.api;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.recruitment.api.dto.RecruitmentAnalyticsDtos.FunnelReport;
import az.millers.hcm.recruitment.api.dto.RecruitmentAnalyticsDtos.SourceReport;
import az.millers.hcm.recruitment.api.dto.RecruitmentAnalyticsDtos.StaleReport;
import az.millers.hcm.recruitment.api.dto.RecruitmentAnalyticsDtos.StaleSummary;
import az.millers.hcm.recruitment.api.dto.RecruitmentAnalyticsDtos.TimeToHireReport;
import az.millers.hcm.recruitment.service.RecruitmentAnalyticsService;

/**
 * Recruitment analytics endpoints (M88). Mounted under
 * {@code /api/reports/recruitment} so the existing Reports family stays
 * the canonical home for analytics.
 */
@RestController
@RequestMapping("/api/reports/recruitment")
public class RecruitmentAnalyticsController {

    private static final String READ_ROLES =
            "hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','RECRUITER','AUDITOR')";

    private final RecruitmentAnalyticsService service;

    public RecruitmentAnalyticsController(RecruitmentAnalyticsService service) {
        this.service = service;
    }

    @GetMapping("/funnel")
    @PreAuthorize(READ_ROLES)
    public FunnelReport funnel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.funnel(from, to);
    }

    @GetMapping("/time-to-hire")
    @PreAuthorize(READ_ROLES)
    public TimeToHireReport timeToHire(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.timeToHire(from, to);
    }

    @GetMapping("/sources")
    @PreAuthorize(READ_ROLES)
    public SourceReport sources(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.sourceEffectiveness(from, to);
    }

    @GetMapping("/stale")
    @PreAuthorize(READ_ROLES)
    public StaleReport stale(@RequestParam(defaultValue = "30") int thresholdDays) {
        return service.stale(thresholdDays);
    }

    /**
     * M89 — lightweight bucketed summary for the home-dashboard widget.
     * Same security envelope as the full report; intentionally distinct path
     * so the tile call doesn't ship the full candidate list down the wire.
     */
    @GetMapping("/stale/summary")
    @PreAuthorize(READ_ROLES)
    public StaleSummary staleSummary(@RequestParam(defaultValue = "30") int thresholdDays) {
        return service.staleSummary(thresholdDays);
    }
}
