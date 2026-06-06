package az.millers.hcm.presence.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.presence.api.PresenceDtos.PresenceSnapshot;
import az.millers.hcm.presence.service.PresenceService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M125 — Presence snapshot endpoint, polled by the SPA on a fixed cadence.
 *
 * <p>READ_HR_PLUS_MANAGERS gates the read: managers see only their team
 * via {@code AccessScopeService}, HR and admins see everyone in scope.
 */
@RestController
@RequestMapping("/api/presence")
public class PresenceController {

    private final PresenceService service;

    public PresenceController(PresenceService service) {
        this.service = service;
    }

    @GetMapping("/snapshot")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public PresenceSnapshot snapshot() {
        return service.snapshot();
    }
}
