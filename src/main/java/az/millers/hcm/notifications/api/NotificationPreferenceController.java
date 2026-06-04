package az.millers.hcm.notifications.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.notifications.domain.NotificationCategory;
import az.millers.hcm.notifications.domain.NotificationChannel;
import az.millers.hcm.notifications.service.NotificationPreferenceService;
import az.millers.hcm.notifications.service.NotificationPreferenceService.PreferenceGrid;
import az.millers.hcm.security.CurrentRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Self-service notification preferences (M115).
 *
 * <p>The two endpoints are scoped to the calling user — there's no concept
 * of "edit someone else's preferences" (would defeat the opt-out purpose).
 * Both require {@code isAuthenticated()}; the actual identity is read from
 * the security context.
 */
@RestController
@RequestMapping("/api/me/notification-preferences")
@PreAuthorize("isAuthenticated()")
public class NotificationPreferenceController {

    private final NotificationPreferenceService service;
    private final CurrentRequest currentRequest;

    public NotificationPreferenceController(NotificationPreferenceService service,
                                              CurrentRequest currentRequest) {
        this.service = service;
        this.currentRequest = currentRequest;
    }

    @GetMapping
    public PreferenceGrid mine() {
        return service.listFor(currentRequest.username());
    }

    public record ToggleRequest(
            @NotNull NotificationCategory category,
            @NotNull NotificationChannel channel,
            @NotNull Boolean enabled) {
    }

    @PostMapping
    public PreferenceGrid toggle(@Valid @RequestBody ToggleRequest req) {
        service.upsert(currentRequest.username(),
                req.category(), req.channel(), req.enabled());
        return service.listFor(currentRequest.username());
    }
}
