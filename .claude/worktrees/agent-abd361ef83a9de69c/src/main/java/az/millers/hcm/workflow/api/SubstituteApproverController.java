package az.millers.hcm.workflow.api;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.workflow.domain.SubstituteApprover;
import az.millers.hcm.workflow.repo.SubstituteApproverRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * M176 — Manage role-level absence cover (PRD §9.1).
 *
 * <p>HR Admins and System Admins may configure substitute approvers so that
 * a holder of {@code substituteRole} can act on behalf of the absent
 * {@code principalRole} during the specified window.
 */
@RestController
@RequestMapping("/api/workflow/substitutes")
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN')")
public class SubstituteApproverController {

    private final SubstituteApproverRepository repo;
    private final CurrentRequest currentRequest;

    public SubstituteApproverController(SubstituteApproverRepository repo,
                                         CurrentRequest currentRequest) {
        this.repo = repo;
        this.currentRequest = currentRequest;
    }

    @GetMapping
    public List<SubstituteApproverResponse> list() {
        return repo.findAll().stream().map(SubstituteApproverResponse::from).toList();
    }

    @GetMapping("/active")
    public List<SubstituteApproverResponse> active() {
        return repo.findActive(LocalDate.now()).stream()
                .map(SubstituteApproverResponse::from).toList();
    }

    @PostMapping
    public SubstituteApproverResponse create(@Valid @RequestBody SubstituteApproverRequest req) {
        SubstituteApprover s = new SubstituteApprover();
        s.setPrincipalRole(req.principalRole().trim());
        s.setSubstituteRole(req.substituteRole().trim());
        s.setStartDate(req.startDate());
        s.setEndDate(req.endDate());
        s.setReason(req.reason());
        s.setCreatedBy(currentRequest.username());
        return SubstituteApproverResponse.from(repo.save(s));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        if (!repo.existsById(id)) throw new ResourceNotFoundException("Substitute not found: " + id);
        repo.deleteById(id);
    }

    // ---------- DTOs ----------

    public record SubstituteApproverRequest(
            @NotBlank String principalRole,
            @NotBlank String substituteRole,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            String reason) {}

    public record SubstituteApproverResponse(
            UUID id,
            String principalRole,
            String substituteRole,
            LocalDate startDate,
            LocalDate endDate,
            boolean active,
            String reason,
            String createdBy,
            OffsetDateTime createdAt) {

        static SubstituteApproverResponse from(SubstituteApprover s) {
            LocalDate today = LocalDate.now();
            return new SubstituteApproverResponse(
                    s.getId(), s.getPrincipalRole(), s.getSubstituteRole(),
                    s.getStartDate(), s.getEndDate(),
                    !today.isBefore(s.getStartDate()) && !today.isAfter(s.getEndDate()),
                    s.getReason(), s.getCreatedBy(), s.getCreatedAt());
        }
    }
}
