package az.millers.hcm.selfservice.api.dto;

/**
 * Mobile branding configuration.
 */
public record MobileBrandingResponse(
        String logoUrl,
        String primaryColor,
        String appName
) {
}
