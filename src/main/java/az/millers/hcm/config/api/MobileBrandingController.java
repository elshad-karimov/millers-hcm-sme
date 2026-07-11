package az.millers.hcm.config.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.config.service.SettingService;
import az.millers.hcm.selfservice.api.dto.MobileBrandingResponse;

/**
 * Mobile branding configuration endpoint.
 */
@RestController
@RequestMapping("/api/config")
public class MobileBrandingController {

    private final SettingService settings;

    public MobileBrandingController(SettingService settings) {
        this.settings = settings;
    }

    /**
     * Returns mobile app branding configuration (logo, color, name).
     * Any authenticated user can read this.
     */
    @GetMapping("/mobile-branding")
    @PreAuthorize("isAuthenticated()")
    public MobileBrandingResponse getMobileBranding() {
        String logoUrl = settings.get("mobile.logo_url",
                "https://millers.az/logo.png");
        String primaryColor = settings.get("mobile.primary_color",
                "#1976D2");
        String appName = settings.get("mobile.app_name",
                "Millers HCM");

        return new MobileBrandingResponse(logoUrl, primaryColor, appName);
    }
}
