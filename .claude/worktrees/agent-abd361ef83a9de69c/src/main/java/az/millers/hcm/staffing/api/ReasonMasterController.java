package az.millers.hcm.staffing.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.security.SecurityRoles;
import az.millers.hcm.staffing.api.dto.ReasonMasterDtos.ReasonRequest;
import az.millers.hcm.staffing.api.dto.ReasonMasterDtos.ReasonResponse;
import az.millers.hcm.staffing.domain.ReasonCategory;
import az.millers.hcm.staffing.service.ReasonMasterService;

/** M259 — REST surface for reason masters (PRD §22). */
@RestController
@RequestMapping("/api/staffing/reasons")
public class ReasonMasterController {

    private final ReasonMasterService service;

    public ReasonMasterController(ReasonMasterService service) {
        this.service = service;
    }

    /**
     * List reasons in a category. Open to anyone who can edit staffing
     * data — the lifecycle/replacement modals need this for the Select.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','RECRUITER','PAYROLL_SPECIALIST')")
    public List<ReasonResponse> list(@RequestParam ReasonCategory category,
                                      @RequestParam(defaultValue = "false") boolean includeInactive) {
        return service.list(category, includeInactive);
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public ReasonResponse create(@Valid @RequestBody ReasonRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public ReasonResponse update(@PathVariable UUID id,
                                  @Valid @RequestBody ReasonRequest req) {
        return service.update(id, req);
    }

    /** Soft delete — active is flipped to false so historical references still resolve. */
    @DeleteMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
