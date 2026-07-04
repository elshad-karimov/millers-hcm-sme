package az.millers.hcm.config.api;

import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.config.domain.TenantSetting;
import az.millers.hcm.config.repo.TenantSettingRepository;
import az.millers.hcm.config.service.SettingService;

/**
 * M433 — Tenant settings admin UI.
 */
@RestController
@RequestMapping("/api/settings")
public class TenantSettingsController {

    private final TenantSettingRepository repo;
    private final SettingService service;

    public TenantSettingsController(TenantSettingRepository repo, SettingService service) {
        this.repo = repo;
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('HR_ADMIN')")
    public List<SettingDto> list() {
        return repo.findByTenantId("default").stream()
                .map(s -> new SettingDto(s.getKey(), s.getValue()))
                .toList();
    }

    @PutMapping
    @PreAuthorize("hasRole('HR_ADMIN')")
    public void upsert(@RequestBody Map<String, String> settings) {
        settings.forEach(service::set);
    }

    public record SettingDto(String key, String value) {}
}
