package az.millers.hcm.policy.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.policy.api.dto.PolicyDtos.AcknowledgementResponse;
import az.millers.hcm.policy.api.dto.PolicyDtos.SelfPolicyView;
import az.millers.hcm.policy.service.PolicyService;

/**
 * M138 — self-service surface. Every authenticated user can browse the
 * PUBLISHED catalogue + acknowledge required policies.
 */
@RestController
@RequestMapping("/api/self/policies")
public class SelfPolicyController {

    private final PolicyService service;

    public SelfPolicyController(PolicyService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<SelfPolicyView> browse() {
        return service.browseForCurrent();
    }

    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("isAuthenticated()")
    public AcknowledgementResponse acknowledge(@PathVariable UUID id) {
        return AcknowledgementResponse.from(service.acknowledge(id));
    }
}
