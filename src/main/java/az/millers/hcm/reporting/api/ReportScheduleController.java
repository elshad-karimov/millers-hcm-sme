package az.millers.hcm.reporting.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.reporting.api.dto.ReportingApiDtos.ScheduleRequest;
import az.millers.hcm.reporting.api.dto.ReportingApiDtos.ScheduleResponse;
import az.millers.hcm.reporting.api.dto.ReportingApiDtos.ScheduleUpdateRequest;
import az.millers.hcm.reporting.service.ReportScheduleService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/reports/schedules")
public class ReportScheduleController {

    private final ReportScheduleService service;

    public ReportScheduleController(ReportScheduleService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<ScheduleResponse> list() {
        return service.list().stream().map(ScheduleResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ScheduleResponse get(@PathVariable UUID id) {
        return ScheduleResponse.from(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public ScheduleResponse create(@Valid @RequestBody ScheduleRequest req) {
        return ScheduleResponse.from(service.create(
                req.name(), req.definitionId(), req.cron(),
                req.recipients(), req.webhookType(), req.webhookUrl(), req.active()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public ScheduleResponse update(@PathVariable UUID id,
                                     @Valid @RequestBody ScheduleUpdateRequest req) {
        return ScheduleResponse.from(service.update(id,
                req.name(), req.cron(), req.recipients(),
                req.webhookType(), req.webhookUrl(), req.active()));
    }

    /** Force-run a schedule now (also useful for testing without waiting). */
    @PostMapping("/{id}/run-now")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
    public ScheduleResponse runNow(@PathVariable UUID id) {
        var s = service.get(id);
        return ScheduleResponse.from(service.runOne(s, java.time.OffsetDateTime.now()));
    }
}
