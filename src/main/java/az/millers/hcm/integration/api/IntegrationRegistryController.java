package az.millers.hcm.integration.api;

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

import az.millers.hcm.integration.domain.IntegrationConfig;
import az.millers.hcm.integration.domain.IntegrationDirection;
import az.millers.hcm.integration.domain.IntegrationLog;
import az.millers.hcm.integration.domain.IntegrationType;
import az.millers.hcm.integration.service.IntegrationRegistryService;

/**
 * M492 — Integration registry endpoints. SYSTEM_ADMIN only.
 */
@RestController
@RequestMapping("/api/admin/integrations")
public class IntegrationRegistryController {

    private static final String SYSTEM_ADMIN_ONLY = "hasRole('SYSTEM_ADMIN')";

    private final IntegrationRegistryService service;

    public IntegrationRegistryController(IntegrationRegistryService service) {
        this.service = service;
    }

    /**
     * List all integration configs.
     */
    @GetMapping
    @PreAuthorize(SYSTEM_ADMIN_ONLY)
    public List<IntegrationConfig> listConfigs() {
        return service.listAll();
    }

    /**
     * Get a single integration config.
     */
    @GetMapping("/{id}")
    @PreAuthorize(SYSTEM_ADMIN_ONLY)
    public IntegrationConfig getConfig(@PathVariable UUID id) {
        return service.get(id);
    }

    /**
     * Create a new integration config.
     */
    @PostMapping
    @PreAuthorize(SYSTEM_ADMIN_ONLY)
    public IntegrationConfig createConfig(
            @RequestBody CreateIntegrationRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        return service.create(
            req.code,
            req.name,
            req.direction,
            req.type,
            req.endpointUrl,
            req.credentialsRef,
            req.notes,
            username
        );
    }

    /**
     * Update an integration config.
     */
    @PutMapping("/{id}")
    @PreAuthorize(SYSTEM_ADMIN_ONLY)
    public IntegrationConfig updateConfig(
            @PathVariable UUID id,
            @RequestBody UpdateIntegrationRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        return service.update(
            id,
            req.name,
            req.direction,
            req.type,
            req.endpointUrl,
            req.credentialsRef,
            req.enabled,
            req.notes,
            username
        );
    }

    /**
     * Delete an integration config.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize(SYSTEM_ADMIN_ONLY)
    public void deleteConfig(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        service.delete(id, username);
    }

    /**
     * Get execution logs for a specific config.
     */
    @GetMapping("/{id}/logs")
    @PreAuthorize(SYSTEM_ADMIN_ONLY)
    public List<IntegrationLog> getConfigLogs(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "50") int limit) {
        return service.getLogsForConfig(id, limit);
    }

    /**
     * Get recent failures across all integrations.
     */
    @GetMapping("/failures")
    @PreAuthorize(SYSTEM_ADMIN_ONLY)
    public List<IntegrationLog> getRecentFailures(
            @RequestParam(defaultValue = "50") int limit) {
        return service.getRecentFailures(limit);
    }

    // ── DTOs ───────────────────────────────────────────────────────────────────

    public record CreateIntegrationRequest(
        String code,
        String name,
        IntegrationDirection direction,
        IntegrationType type,
        String endpointUrl,
        String credentialsRef,
        String notes
    ) {}

    public record UpdateIntegrationRequest(
        String name,
        IntegrationDirection direction,
        IntegrationType type,
        String endpointUrl,
        String credentialsRef,
        boolean enabled,
        String notes
    ) {}
}
