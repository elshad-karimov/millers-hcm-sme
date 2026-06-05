package az.millers.hcm.preboarding.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.preboarding.api.PreboardingDtos.PublicInfo;
import az.millers.hcm.preboarding.api.PreboardingDtos.SubmitRequest;
import az.millers.hcm.preboarding.api.PreboardingDtos.SubmitResponse;
import az.millers.hcm.preboarding.domain.PreboardingInvite;
import az.millers.hcm.preboarding.service.PreboardingInviteService;

/**
 * M122 — public candidate-facing surface. SecurityConfig adds
 * {@code /api/public/preboarding/**} to {@code permitAll} so this works
 * without a Keycloak JWT — the token IS the credential.
 *
 * <p>Unknown / expired / revoked tokens always map to 404 to avoid
 * leaking which tokens have ever existed.
 */
@RestController
@RequestMapping("/api/public/preboarding")
public class PublicPreboardingController {

    private final PreboardingInviteService service;

    public PublicPreboardingController(PreboardingInviteService service) {
        this.service = service;
    }

    @GetMapping("/{token}/info")
    public ResponseEntity<PublicInfo> info(@PathVariable String token) {
        PreboardingInvite invite = service.resolveByToken(token);
        if (invite == null) return ResponseEntity.notFound().build();
        service.markOpenedIfNeeded(invite);
        return ResponseEntity.ok(service.publicInfo(
                invite, service.loadEmployee(invite).orElse(null)));
    }

    @PostMapping("/{token}/submit")
    public ResponseEntity<SubmitResponse> submit(@PathVariable String token,
                                                 @RequestBody SubmitRequest req) {
        PreboardingInvite invite = service.resolveByToken(token);
        if (invite == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(
                service.submit(invite, req == null ? null : req.payload()));
    }
}
