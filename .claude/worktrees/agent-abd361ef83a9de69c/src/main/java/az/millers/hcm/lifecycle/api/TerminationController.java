package az.millers.hcm.lifecycle.api;

import az.millers.hcm.security.SecurityRoles;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.common.PageResponse;
import az.millers.hcm.lifecycle.api.dto.ClearanceUpdateRequest;
import az.millers.hcm.lifecycle.api.dto.ExitInterviewRequest;
import az.millers.hcm.lifecycle.api.dto.ExitInterviewResponse;
import az.millers.hcm.lifecycle.api.dto.TerminationResponse;
import az.millers.hcm.lifecycle.api.dto.TerminationSubmitRequest;
import az.millers.hcm.lifecycle.domain.TerminationRequest;
import az.millers.hcm.lifecycle.domain.TerminationStatus;
import az.millers.hcm.lifecycle.service.TerminationService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/lifecycle/terminations")
public class TerminationController {

    private final TerminationService service;

    public TerminationController(TerminationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public PageResponse<TerminationResponse> list(
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) TerminationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<TerminationRequest> result = service.list(employeeId, status, PageRequest.of(page, size));
        return PageResponse.of(result, TerminationResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public TerminationResponse get(@PathVariable UUID id) {
        return TerminationResponse.from(service.get(id));
    }

    @PostMapping("/submit")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST')")
    public TerminationResponse submit(@Valid @RequestBody TerminationSubmitRequest req) {
        return TerminationResponse.from(service.submit(req));
    }

    @PostMapping("/{id}/clearance")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public TerminationResponse updateClearance(@PathVariable UUID id,
                                                @RequestBody ClearanceUpdateRequest req) {
        return TerminationResponse.from(service.updateClearance(id, req));
    }

    @GetMapping("/{id}/exit-interview")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST')")
    public ExitInterviewResponse getExitInterview(@PathVariable UUID id) {
        return ExitInterviewResponse.from(service.getExitInterview(id));
    }

    @PostMapping("/{id}/exit-interview")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST')")
    public ExitInterviewResponse recordExitInterview(@PathVariable UUID id,
                                                       @Valid @RequestBody ExitInterviewRequest req) {
        return ExitInterviewResponse.from(service.recordExitInterview(id, req));
    }

    @PostMapping("/{id}/process")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public TerminationResponse process(@PathVariable UUID id) {
        return TerminationResponse.from(service.process(id));
    }

    /** M216 — HR_ADMIN can cancel a pending termination request. */
    @PostMapping("/{id}/cancel")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public TerminationResponse cancel(@PathVariable UUID id,
                                       @RequestParam(required = false) String reason) {
        return TerminationResponse.from(service.onCancelled(id, reason));
    }
}
