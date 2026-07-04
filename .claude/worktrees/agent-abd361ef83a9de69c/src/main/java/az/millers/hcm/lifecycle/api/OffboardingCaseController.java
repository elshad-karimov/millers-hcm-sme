package az.millers.hcm.lifecycle.api;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.common.PageResponse;
import az.millers.hcm.lifecycle.api.dto.ChecklistDtos.AssignmentResponse;
import az.millers.hcm.lifecycle.api.dto.OffboardingDtos.OffboardingCaseResponse;
import az.millers.hcm.lifecycle.api.dto.OffboardingDtos.OffboardingOverviewResponse;
import az.millers.hcm.lifecycle.domain.OffboardingCase;
import az.millers.hcm.lifecycle.domain.OffboardingCaseStatus;
import az.millers.hcm.lifecycle.service.ChecklistService;
import az.millers.hcm.lifecycle.service.OffboardingCaseService;
import az.millers.hcm.security.SecurityRoles;

@RestController
@RequestMapping("/api/lifecycle/offboarding/cases")
public class OffboardingCaseController {

    private final OffboardingCaseService service;
    private final ChecklistService checklists;

    public OffboardingCaseController(OffboardingCaseService service, ChecklistService checklists) {
        this.service = service;
        this.checklists = checklists;
    }

    @GetMapping("/overview")
    @PreAuthorize(SecurityRoles.READ_HR)
    public OffboardingOverviewResponse overview() {
        return service.overview();
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public PageResponse<OffboardingCaseResponse> list(
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) OffboardingCaseStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(service.list(employeeId, status, PageRequest.of(page, size)),
                OffboardingCaseResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_HR)
    public OffboardingCaseResponse get(@PathVariable UUID id) {
        return OffboardingCaseResponse.from(service.get(id));
    }

    @GetMapping("/{id}/checklist")
    @PreAuthorize(SecurityRoles.READ_HR)
    public ResponseEntity<AssignmentResponse> getChecklist(@PathVariable UUID id) {
        OffboardingCase c = service.get(id);
        if (c.getChecklistAssignmentId() == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(checklists.getAssignment(c.getChecklistAssignmentId()));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public OffboardingCaseResponse updateStatus(
            @PathVariable UUID id,
            @RequestParam OffboardingCaseStatus status,
            @RequestParam(required = false) String notes) {
        return OffboardingCaseResponse.from(service.updateStatus(id, status, notes));
    }
}
