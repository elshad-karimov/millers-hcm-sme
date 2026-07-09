package az.millers.hcm.compliance.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.compliance.domain.PrivacyRequest;
import az.millers.hcm.compliance.domain.PrivacyRequest.RequestStatus;
import az.millers.hcm.compliance.service.PrivacyRequestService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M472 — Privacy request endpoints (HR_ADMIN only).
 */
@RestController
@RequestMapping("/api/compliance/privacy-requests")
public class PrivacyRequestController {

    private final PrivacyRequestService service;

    public PrivacyRequestController(PrivacyRequestService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public ResponseEntity<List<PrivacyRequest>> list() {
        return ResponseEntity.ok(service.list());
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_HR)
    public ResponseEntity<PrivacyRequest> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public ResponseEntity<PrivacyRequest> create(@RequestBody PrivacyRequest request) {
        return ResponseEntity.ok(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public ResponseEntity<PrivacyRequest> update(@PathVariable UUID id,
                                                  @RequestBody PrivacyRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public ResponseEntity<PrivacyRequest> updateStatus(@PathVariable UUID id,
                                                         @RequestBody StatusUpdateRequest request) {
        return ResponseEntity.ok(service.updateStatus(id, request.status(), request.resolutionNotes()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    public record StatusUpdateRequest(RequestStatus status, String resolutionNotes) {}
}
