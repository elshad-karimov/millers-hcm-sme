package az.millers.hcm.security.tenant;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import az.millers.hcm.common.tenant.TenantContext;
import az.millers.hcm.common.tenant.TenantRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Binds {@link TenantContext} for the duration of each authenticated request
 * (multi-tenancy Phase 3).
 *
 * <p>Runs immediately after the bearer-token authentication filter. When the
 * request carries a validated JWT, it maps the token's {@code iss} claim to a
 * tenant id via the {@link TenantRegistry} and binds it so Hibernate's
 * {@code @TenantId} filter and every {@code TenantContext.current()} native-SQL
 * site scope to the caller's tenant. The binding is always cleared in a
 * {@code finally} to protect pooled request threads.
 *
 * <p>Requests without a JWT (public endpoints, API-key auth) leave the context
 * unbound, so {@code TenantContext.current()} falls back to
 * {@link TenantContext#DEFAULT} — unchanged single-tenant behaviour.
 */
public class TenantResolutionFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantResolutionFilter.class);

    private final TenantRegistry registry;

    public TenantResolutionFilter(TenantRegistry registry) {
        this.registry = registry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        boolean bound = false;
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken jwtAuth) {
                Jwt jwt = jwtAuth.getToken();
                String issuer = jwt.getIssuer() != null ? jwt.getIssuer().toString()
                        : jwt.getClaimAsString("iss");
                if (issuer != null) {
                    String tenantId = registry.tenantForIssuer(issuer).orElse(null);
                    if (tenantId != null) {
                        TenantContext.set(tenantId);
                        bound = true;
                    } else {
                        // Should not happen: the auth resolver only admits trusted
                        // issuers. Log and leave unbound (→ DEFAULT) defensively.
                        log.warn("Authenticated token issuer {} not in tenant registry", issuer);
                    }
                }
            }
            chain.doFilter(request, response);
        } finally {
            if (bound) {
                TenantContext.clear();
            }
        }
    }
}
