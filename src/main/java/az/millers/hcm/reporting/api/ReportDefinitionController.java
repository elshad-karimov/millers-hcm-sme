package az.millers.hcm.reporting.api;

import az.millers.hcm.security.SecurityRoles;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.reporting.api.dto.ReportingApiDtos.DefinitionRequest;
import az.millers.hcm.reporting.api.dto.ReportingApiDtos.DefinitionResponse;
import az.millers.hcm.reporting.api.dto.ReportingApiDtos.RunResponse;
import az.millers.hcm.reporting.domain.ReportFormat;
import az.millers.hcm.reporting.domain.ReportType;
import az.millers.hcm.reporting.service.ReportDefinitionService;
import az.millers.hcm.reporting.service.ReportRunService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/reports/definitions")
public class ReportDefinitionController {

    private final ReportDefinitionService service;
    private final ReportRunService runService;

    public ReportDefinitionController(ReportDefinitionService service, ReportRunService runService) {
        this.service = service;
        this.runService = runService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<DefinitionResponse> list(@RequestParam(required = false) ReportType type,
                                          @RequestParam(required = false) Boolean activeOnly) {
        return service.list(type, activeOnly).stream().map(DefinitionResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public DefinitionResponse get(@PathVariable UUID id) {
        return DefinitionResponse.from(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public DefinitionResponse create(@Valid @RequestBody DefinitionRequest req) {
        return DefinitionResponse.from(service.create(
                req.name(), req.reportType(), req.defaultFormat(),
                req.parameters(), req.description(), req.active()));
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public DefinitionResponse update(@PathVariable UUID id, @Valid @RequestBody DefinitionRequest req) {
        return DefinitionResponse.from(service.update(id,
                req.name(), req.reportType(), req.defaultFormat(),
                req.parameters(), req.description(), req.active()));
    }

    /** Trigger an ad-hoc run of a saved definition. */
    @PostMapping("/{id}/run")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN','HR_SPECIALIST')")
    public RunResponse run(@PathVariable UUID id,
                           @RequestParam(required = false) ReportFormat format) {
        return RunResponse.from(runService.runDefinition(id, format));
    }
}
