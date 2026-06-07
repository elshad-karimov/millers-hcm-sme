package az.millers.hcm.policy.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.policy.api.dto.PolicyDtos.AcknowledgementResponse;
import az.millers.hcm.policy.api.dto.PolicyDtos.PolicyRequest;
import az.millers.hcm.policy.api.dto.PolicyDtos.PolicyResponse;
import az.millers.hcm.policy.domain.PolicyStatus;
import az.millers.hcm.policy.service.PolicyService;
import az.millers.hcm.security.SecurityRoles;
import jakarta.validation.Valid;

/**
 * M138 — HR admin surface for the policy library. Mutations gated on
 * {@link SecurityRoles#WRITE_HR_ADMIN_ONLY}; reads on
 * {@link SecurityRoles#READ_HR_PLUS_MANAGERS} so line managers can
 * browse the catalogue too.
 */
@RestController
@RequestMapping("/api/policies")
public class PolicyAdminController {

    private final PolicyService service;

    public PolicyAdminController(PolicyService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<PolicyResponse> list(@RequestParam(required = false) PolicyStatus status) {
        return service.list(status).stream().map(PolicyResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public PolicyResponse get(@PathVariable UUID id) {
        return PolicyResponse.from(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public PolicyResponse create(@Valid @RequestBody PolicyRequest req) {
        return PolicyResponse.from(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public PolicyResponse update(@PathVariable UUID id,
                                  @Valid @RequestBody PolicyRequest req) {
        return PolicyResponse.from(service.update(id, req));
    }

    @PostMapping("/{id}/status/{target}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public PolicyResponse changeStatus(@PathVariable UUID id,
                                        @PathVariable PolicyStatus target) {
        return PolicyResponse.from(service.changeStatus(id, target));
    }

    /**
     * Who has acknowledged this policy. Pair with the upcoming "pending"
     * report (employees minus ackers) for compliance dashboards.
     */
    @GetMapping("/{id}/acknowledgements")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<AcknowledgementResponse> acknowledgements(@PathVariable UUID id) {
        return service.acknowledgementsFor(id).stream()
                .map(AcknowledgementResponse::from)
                .toList();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public void deletePolicy(@PathVariable UUID id) {
        // Hard delete is intentionally not implemented — policies are
        // historical artefacts. Use ARCHIVE on /status/ARCHIVED instead.
        throw new az.millers.hcm.common.BadRequestException(
                "Policies cannot be deleted; archive them via /status/ARCHIVED instead.");
    }
}
