package az.millers.hcm.notifications.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.notifications.domain.DeliveryLog;
import az.millers.hcm.notifications.domain.DeliveryStatus;
import az.millers.hcm.notifications.domain.NotificationChannel;
import az.millers.hcm.notifications.domain.NotificationTemplate;
import az.millers.hcm.notifications.service.NotificationTemplateService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M493 — Notification templates + delivery logs. HR_ADMIN only.
 */
@RestController
@RequestMapping("/api/notifications/templates")
public class NotificationTemplateController {

    private final NotificationTemplateService service;

    public NotificationTemplateController(NotificationTemplateService service) {
        this.service = service;
    }

    /**
     * List all templates.
     */
    @GetMapping
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public List<NotificationTemplate> listTemplates() {
        return service.listAll();
    }

    /**
     * Get a single template.
     */
    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public NotificationTemplate getTemplate(@PathVariable UUID id) {
        return service.get(id);
    }

    /**
     * Create a new template.
     */
    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public NotificationTemplate createTemplate(
            @RequestBody CreateTemplateRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        return service.create(
            req.code,
            req.name,
            req.channel,
            req.subjectTemplate,
            req.bodyTemplate,
            username
        );
    }

    /**
     * Update a template.
     */
    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public NotificationTemplate updateTemplate(
            @PathVariable UUID id,
            @RequestBody UpdateTemplateRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        return service.update(
            id,
            req.name,
            req.channel,
            req.subjectTemplate,
            req.bodyTemplate,
            req.active,
            username
        );
    }

    /**
     * Delete a template.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public void deleteTemplate(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        service.delete(id, username);
    }

    /**
     * Get delivery logs with filters.
     */
    @GetMapping("/delivery-logs")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public List<DeliveryLog> getDeliveryLogs(
            @RequestParam(required = false) NotificationChannel channel,
            @RequestParam(required = false) DeliveryStatus status,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(defaultValue = "100") int limit) {
        return service.getDeliveryLogs(channel, status, from, to, limit);
    }

    // ── DTOs ───────────────────────────────────────────────────────────────────

    public record CreateTemplateRequest(
        String code,
        String name,
        NotificationChannel channel,
        String subjectTemplate,
        String bodyTemplate
    ) {}

    public record UpdateTemplateRequest(
        String name,
        NotificationChannel channel,
        String subjectTemplate,
        String bodyTemplate,
        boolean active
    ) {}
}
