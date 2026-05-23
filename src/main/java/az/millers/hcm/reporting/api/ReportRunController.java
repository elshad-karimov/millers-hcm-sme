package az.millers.hcm.reporting.api;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.common.PageResponse;
import az.millers.hcm.reporting.api.dto.ReportingApiDtos.RunResponse;
import az.millers.hcm.reporting.domain.ReportRun;
import az.millers.hcm.reporting.service.ReportRunService;

@RestController
@RequestMapping("/api/reports/runs")
public class ReportRunController {

    private final ReportRunService service;

    public ReportRunController(ReportRunService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public PageResponse<RunResponse> list(@RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "50") int size) {
        Page<ReportRun> result = service.list(PageRequest.of(page, size));
        return PageResponse.of(result, RunResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public RunResponse get(@PathVariable UUID id) {
        return RunResponse.from(service.get(id));
    }

    /**
     * Re-attempt email delivery for a SUCCESS run. Useful after fixing
     * an SMTP outage or correcting a typo in the schedule's recipients.
     * Restricted to HR/admin since it spends an outbound email.
     */
    @PostMapping("/{id}/resend-email")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public RunResponse resendEmail(@PathVariable UUID id) {
        return RunResponse.from(service.resendEmail(id));
    }

    /**
     * Re-fire the Slack/Teams webhook for a SUCCESS run. Useful after
     * fixing a webhook URL or rotating a Slack incoming-webhook token.
     */
    @PostMapping("/{id}/resend-webhook")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public RunResponse resendWebhook(@PathVariable UUID id) {
        return RunResponse.from(service.resendWebhook(id));
    }
}
