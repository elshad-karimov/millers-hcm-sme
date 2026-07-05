package az.millers.hcm.ehs.api;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import az.millers.hcm.ehs.api.dto.CorrectiveActionDtos.*;
import az.millers.hcm.ehs.domain.CorrectiveAction;
import az.millers.hcm.ehs.domain.CorrectiveActionStatus;
import az.millers.hcm.ehs.service.CorrectiveActionService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M451 — EHS corrective action management.
 */
@RestController
@RequestMapping("/api/ehs/corrective-actions")
public class CorrectiveActionController {

    private final CorrectiveActionService service;

    public CorrectiveActionController(CorrectiveActionService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public CorrectiveActionResponse create(@RequestBody CreateCorrectiveActionRequest req) {
        CorrectiveAction action = service.create(
                req.incidentId(),
                req.inspectionId(),
                req.riskAssessmentId(),
                req.description(),
                req.responsibleUsername(),
                req.dueDate(),
                req.priority()
        );

        return CorrectiveActionResponse.from(action);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public CorrectiveActionResponse updateStatus(@PathVariable UUID id,
                                                   @RequestBody UpdateCorrectiveActionStatusRequest req) {
        CorrectiveAction action = service.updateStatus(id, req.status(), req.evidenceAttachmentId());
        return CorrectiveActionResponse.from(action);
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_HR)
    public CorrectiveActionResponse get(@PathVariable UUID id) {
        CorrectiveAction action = service.get(id);
        return CorrectiveActionResponse.from(action);
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<CorrectiveActionResponse> list(
            @RequestParam(required = false) CorrectiveActionStatus status,
            @RequestParam(required = false) String responsibleUsername) {

        return service.list(status, responsibleUsername).stream()
                .map(CorrectiveActionResponse::from)
                .collect(Collectors.toList());
    }
}
