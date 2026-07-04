package az.millers.hcm.leave.api;

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

import az.millers.hcm.leave.api.BlackoutDtos.BlackoutRequest;
import az.millers.hcm.leave.api.BlackoutDtos.BlackoutResponse;
import az.millers.hcm.leave.api.BlackoutDtos.PreviewRequest;
import az.millers.hcm.leave.api.BlackoutDtos.PreviewResponse;
import az.millers.hcm.leave.service.BlackoutWindowService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M123 — admin CRUD on blackout windows + leave-form preview endpoint.
 *
 * <p>CRUD is HR_ADMIN-only — blackouts have org-wide effect, so issuing
 * one is a more significant decision than a regular leave-type tweak.
 * Preview is open to everyone with leave-request access so the leave
 * form can show conflicts before the user clicks Submit.
 */
@RestController
@RequestMapping("/api/leave/blackouts")
public class BlackoutWindowController {

    private final BlackoutWindowService service;

    public BlackoutWindowController(BlackoutWindowService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<BlackoutResponse> list() {
        return service.listAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_HR)
    public BlackoutResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public BlackoutResponse create(@RequestBody BlackoutRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public BlackoutResponse update(@PathVariable UUID id, @RequestBody BlackoutRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Leave-form preview: returns the worst severity + list of matches
     * so the SPA can show a conflict banner before the user clicks
     * Submit. Authenticated read is sufficient — anyone allowed to file
     * a leave request can call this.
     */
    @PostMapping("/preview")
    public PreviewResponse preview(@RequestBody PreviewRequest req) {
        return service.preview(req);
    }
}
