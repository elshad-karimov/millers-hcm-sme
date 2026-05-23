package az.millers.hcm.security.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import az.millers.hcm.audit.AuditService;

/**
 * Writes one {@code SECURITY/Login → TOKEN_ACCEPTED} audit row the first time
 * we see a given JWT. Without this hook, every authenticated request would
 * publish an {@code AuthenticationSuccessEvent}, swamping the audit log
 * once the bearer token is in place.
 *
 * <p>Dedup key is {@code jti} when present (Keycloak always issues one), with
 * {@code sub + iat} as a fallback for tokens that don't carry it.
 *
 * <p>Login {@code SUCCESS} / {@code FAILURE} / {@code LOCKED} events
 * themselves now live in Keycloak's own event log — this row exists so the
 * API-side audit log retains a breadcrumb tied to the rest of the actor's
 * actions in the same session.
 */
@Component
public class TokenAcceptedAuditListener {

    private static final Logger log = LoggerFactory.getLogger(TokenAcceptedAuditListener.class);
    private static final String MODULE = "SECURITY";

    /**
     * Tracks the first-seen instant per jti so we don't double-audit. Pruned
     * lazily — entries older than 24h are removed when the map is touched
     * after a sighting, so memory stays bounded under churn.
     */
    private final Map<String, Instant> seen = new ConcurrentHashMap<>();
    private static final Duration RETENTION = Duration.ofHours(24);

    private final AuditService audit;

    public TokenAcceptedAuditListener(AuditService audit) {
        this.audit = audit;
    }

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        if (!(event.getAuthentication() instanceof JwtAuthenticationToken jwtAuth)) {
            return;
        }
        Jwt jwt = jwtAuth.getToken();
        String dedupKey = jwt.getId() != null
                ? jwt.getId()
                : jwt.getSubject() + ":" + jwt.getIssuedAt();
        Instant now = Instant.now();
        if (seen.putIfAbsent(dedupKey, now) != null) {
            return; // already audited this token
        }
        pruneStale(now);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("issuer", jwt.getIssuer() != null ? jwt.getIssuer().toString() : null);
        meta.put("jti", jwt.getId());
        meta.put("expiresAt", jwt.getExpiresAt());
        meta.put("roles", jwtAuth.getAuthorities().stream()
                .map(a -> a.getAuthority().startsWith("ROLE_")
                        ? a.getAuthority().substring(5)
                        : a.getAuthority())
                .toList());
        try {
            audit.record(MODULE, "Login", jwtAuth.getName(), "TOKEN_ACCEPTED", null, meta);
        } catch (Exception ex) {
            // Don't let an audit hiccup break the request — fail soft + log.
            log.warn("Failed to write SECURITY/Login TOKEN_ACCEPTED audit row for {}: {}",
                    jwtAuth.getName(), ex.getMessage());
        }
    }

    private void pruneStale(Instant now) {
        Instant cutoff = now.minus(RETENTION);
        seen.entrySet().removeIf(e -> e.getValue().isBefore(cutoff));
    }
}
