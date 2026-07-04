package az.millers.hcm.common.expiry.api;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.common.expiry.ExpiryAlertScheduler;
import az.millers.hcm.common.expiry.ExpiryAlertScheduler.ScanSummary;
import az.millers.hcm.common.expiry.ExpiryAlertSource;

/**
 * Administrative interface for the M61 / M68 expiry-alert pipeline.
 *
 * <p>Two operational endpoints:
 * <ul>
 *   <li>{@code GET /api/admin/expiry/sources} — debug introspection, lists
 *       every {@link ExpiryAlertSource} bean Spring discovered. Useful for
 *       confirming a new source was wired correctly after a deploy.</li>
 *   <li>{@code POST /api/admin/expiry/scan} — manual scan trigger.
 *       Idempotent (the M68 audit-log dedup means re-running on the same
 *       day is a no-op). Optional {@code date} parameter lets HR drive a
 *       what-if scan for a future date.</li>
 * </ul>
 *
 * <p>Both gated to HR_ADMIN / SYSTEM_ADMIN — the alerts they trigger fan out
 * to real employees, so write access is intentionally restricted.
 */
@RestController
@RequestMapping("/api/admin/expiry")
public class ExpiryAlertController {

    private static final String ADMIN_ROLES = "hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')";

    private final ExpiryAlertScheduler scheduler;

    public ExpiryAlertController(ExpiryAlertScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @GetMapping("/sources")
    @PreAuthorize(ADMIN_ROLES)
    public List<SourceView> sources() {
        return scheduler.getSources().stream()
                .map(s -> new SourceView(s.moduleName(), s.entityName(), s.getClass().getSimpleName()))
                .toList();
    }

    /**
     * Trigger a scan now. The cron-driven daily run uses the same code path —
     * this endpoint is for off-cycle / verification runs.
     *
     * @param date  optional "as of" day; defaults to today. Useful for a
     *              dry-run "what would have fired yesterday?" check.
     */
    @PostMapping("/scan")
    @PreAuthorize(ADMIN_ROLES)
    public ScanSummary scan(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return scheduler.scanFor(date != null ? date : LocalDate.now());
    }

    public record SourceView(String moduleName, String entityName, String beanClass) {}
}
