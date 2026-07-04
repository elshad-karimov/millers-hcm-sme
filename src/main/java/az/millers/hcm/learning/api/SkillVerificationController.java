package az.millers.hcm.learning.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.learning.api.dto.SkillVerificationRequestDto;
import az.millers.hcm.learning.api.dto.SkillVerificationResponse;
import az.millers.hcm.learning.domain.SkillVerificationRequest;
import az.millers.hcm.learning.service.SkillVerificationService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M419: Skill verification workflow.
 */
@RestController
@RequestMapping("/api/skills/verifications")
public class SkillVerificationController {

    private final SkillVerificationService service;

    public SkillVerificationController(SkillVerificationService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public SkillVerificationResponse submit(@RequestBody SkillVerificationRequestDto req) {
        SkillVerificationRequest saved = service.submitRequest(
                req.competencyId(), req.requestedLevel(), req.evidenceNotes());
        return SkillVerificationResponse.from(saved);
    }

    @GetMapping("/pending")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<SkillVerificationResponse> getPending() {
        return service.getPendingRequests().stream()
                .map(SkillVerificationResponse::from)
                .toList();
    }

    @GetMapping("/my-requests")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<SkillVerificationResponse> getMyRequests() {
        return service.getMyRequests().stream()
                .map(SkillVerificationResponse::from)
                .toList();
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public SkillVerificationResponse approve(@PathVariable UUID id,
                                               @RequestBody(required = false) VerificationNoteRequest req) {
        String notes = req != null ? req.notes() : null;
        SkillVerificationRequest saved = service.approve(id, notes);
        return SkillVerificationResponse.from(saved);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public SkillVerificationResponse reject(@PathVariable UUID id,
                                              @RequestBody(required = false) VerificationNoteRequest req) {
        String notes = req != null ? req.notes() : null;
        SkillVerificationRequest saved = service.reject(id, notes);
        return SkillVerificationResponse.from(saved);
    }

    public record VerificationNoteRequest(String notes) {}
}
