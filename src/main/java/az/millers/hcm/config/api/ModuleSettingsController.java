package az.millers.hcm.config.api;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.config.service.SettingService;

/**
 * Per-tenant module enablement.
 *
 * Readable by ANY authenticated user (unlike /api/settings which is HR_ADMIN
 * only) so the SPA navigation can hide modules the tenant has switched off for
 * every user, not just admins. Writes go through the existing admin-only
 * PUT /api/settings using the "disabled_modules" key.
 */
@RestController
@RequestMapping("/api/module-settings")
public class ModuleSettingsController {

    private final SettingService settings;

    public ModuleSettingsController(SettingService settings) {
        this.settings = settings;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ModuleSettingsDto get() {
        return new ModuleSettingsDto(settings.disabledModules());
    }

    /** @param disabled module keys the tenant has switched off. */
    public record ModuleSettingsDto(List<String> disabled) {}
}
