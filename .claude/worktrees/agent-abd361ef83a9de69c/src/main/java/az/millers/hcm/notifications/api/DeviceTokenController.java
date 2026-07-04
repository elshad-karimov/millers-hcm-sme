package az.millers.hcm.notifications.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.notifications.domain.DeviceToken;
import az.millers.hcm.notifications.repo.DeviceTokenRepository;
import jakarta.validation.Valid;

/**
 * REST endpoints for managing FCM device tokens (PRD §17.5).
 *
 * <pre>
 * POST   /api/notifications/device-token   — register a token (upsert)
 * DELETE /api/notifications/device-token   — deregister a token
 * </pre>
 */
@RestController
@RequestMapping("/api/notifications/device-token")
public class DeviceTokenController {

    private final DeviceTokenRepository tokenRepo;

    public DeviceTokenController(DeviceTokenRepository tokenRepo) {
        this.tokenRepo = tokenRepo;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public void register(@Valid @RequestBody DeviceTokenRequest req,
                         @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        // Upsert: delete any existing record for this token then re-insert
        tokenRepo.deleteByUsernameAndFcmToken(username, req.fcmToken());
        DeviceToken dt = new DeviceToken();
        dt.setUsername(username);
        dt.setFcmToken(req.fcmToken());
        dt.setPlatform(req.platform() != null ? req.platform() : "FLUTTER");
        tokenRepo.save(dt);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deregister(@Valid @RequestBody DeviceTokenRequest req,
                           @AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        tokenRepo.deleteByUsernameAndFcmToken(username, req.fcmToken());
    }
}
