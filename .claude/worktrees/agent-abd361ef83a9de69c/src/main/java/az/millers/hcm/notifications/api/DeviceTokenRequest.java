package az.millers.hcm.notifications.api;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for registering or deregistering an FCM device token.
 */
public record DeviceTokenRequest(
        @NotBlank String fcmToken,
        String platform) {
}
