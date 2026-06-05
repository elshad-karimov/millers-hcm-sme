package az.millers.hcm.apikey.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import az.millers.hcm.apikey.domain.ApiKey;
import az.millers.hcm.apikey.service.ApiKeyService;
import az.millers.hcm.apikey.service.TokenBucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * M120 — recognises {@code X-API-Key: hcm_…} headers and authenticates
 * the request without a Keycloak JWT.
 *
 * <p>Pipeline:
 * <ol>
 *   <li>If the header isn't present → fall through to the JWT filter.</li>
 *   <li>If present but unknown / revoked / expired → 401 + opaque body.</li>
 *   <li>If valid → consume one token from the per-key
 *       {@link TokenBucket}. If the bucket is empty, respond 429 with a
 *       {@code Retry-After} header (don't authenticate — the caller
 *       hasn't earned a request).</li>
 *   <li>Otherwise set an {@link ApiKeyAuthentication} on the security
 *       context with the key's granted scopes mapped to {@code ROLE_*}
 *       authorities, then continue the chain.</li>
 * </ol>
 *
 * <p>Usage is recorded best-effort after the chain completes; failures
 * there never break a request.
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";

    private final ApiKeyService apiKeyService;

    public ApiKeyAuthFilter(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (header == null || header.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        ApiKey key = apiKeyService.resolve(header);
        if (key == null) {
            // Opaque body so a probe can't distinguish "unknown" from "expired".
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"invalid_api_key\"}");
            return;
        }

        long now = System.nanoTime();
        TokenBucket bucket = TokenBucket.get(key.getId(), key.getRateLimitPerMin(), now);
        if (!bucket.tryConsume(now)) {
            long retry = bucket.retryAfterSeconds(now);
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retry));
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"rate_limited\",\"retryAfterSeconds\":" + retry + "}");
            // Best-effort record the rejection.
            try {
                apiKeyService.recordUsage(key.getId(), request.getRemoteAddr(), false);
            } catch (RuntimeException ignored) {}
            return;
        }

        // Authenticate.
        List<GrantedAuthority> auths = new ArrayList<>(key.getScopes().size());
        for (String s : key.getScopes()) auths.add(new SimpleGrantedAuthority("ROLE_" + s));
        ApiKeyAuthentication token = new ApiKeyAuthentication(key.getOwnerUser(), auths, key.getId());
        SecurityContextHolder.getContext().setAuthentication(token);

        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
            try {
                apiKeyService.recordUsage(key.getId(), request.getRemoteAddr(), true);
            } catch (RuntimeException ignored) {}
        }
    }

    /**
     * Authentication token for an API-key principal. Carries the owner
     * username as the principal name (so audit rows attribute correctly)
     * and the API key id as the credentials slot (so downstream code can
     * tell key-auth apart from JWT-auth if it ever needs to).
     */
    public static final class ApiKeyAuthentication extends AbstractAuthenticationToken {
        private final String username;
        private final java.util.UUID apiKeyId;

        public ApiKeyAuthentication(String username,
                                    List<GrantedAuthority> authorities,
                                    java.util.UUID apiKeyId) {
            super(authorities);
            this.username = username;
            this.apiKeyId = apiKeyId;
            setAuthenticated(true);
        }

        @Override public Object getCredentials() { return apiKeyId; }
        @Override public Object getPrincipal()   { return username; }
        @Override public String getName()        { return username; }
        public java.util.UUID apiKeyId()         { return apiKeyId; }
    }
}
