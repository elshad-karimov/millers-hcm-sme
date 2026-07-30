package az.millers.hcm.security.tenant;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

import az.millers.hcm.common.tenant.TenantRegistry;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Multi-issuer resource-server authentication resolver (multi-tenancy Phase 3).
 *
 * <p>Replaces the single fixed {@code JwtDecoder}. For each incoming bearer
 * token, {@link JwtIssuerAuthenticationManagerResolver} peeks the {@code iss}
 * claim and delegates to a per-issuer {@link AuthenticationManager}:
 * <ol>
 *   <li>the issuer must map to an <b>active tenant</b> in the {@link TenantRegistry},
 *       otherwise the token is rejected ({@link InvalidBearerTokenException} → 401);</li>
 *   <li>a {@link JwtAuthenticationProvider} is built lazily per issuer with a
 *       decoder from {@link KeycloakJwtDecoderFactory} plus the shared role/username
 *       {@link JwtAuthenticationConverter}, and cached.</li>
 * </ol>
 *
 * <p>The registry is consulted per token (its own snapshot is cached), so a
 * tenant provisioned at runtime becomes trusted as soon as
 * {@link TenantRegistry#refresh()} runs — no restart.
 */
@Component
public class TenantAuthenticationResolver
        implements AuthenticationManagerResolver<HttpServletRequest> {

    private final JwtIssuerAuthenticationManagerResolver delegate;
    private final ConcurrentHashMap<String, AuthenticationManager> managers = new ConcurrentHashMap<>();

    public TenantAuthenticationResolver(TenantRegistry registry,
                                        KeycloakJwtDecoderFactory decoderFactory,
                                        JwtAuthenticationConverter authenticationConverter) {
        AuthenticationManagerResolver<String> byIssuer = issuer -> {
            if (!registry.isTrustedIssuer(issuer)) {
                throw new InvalidBearerTokenException(
                        "Untrusted token issuer: " + issuer);
            }
            return managers.computeIfAbsent(issuer, iss -> {
                JwtDecoder decoder = decoderFactory.build(iss);
                JwtAuthenticationProvider provider = new JwtAuthenticationProvider(decoder);
                provider.setJwtAuthenticationConverter(authenticationConverter);
                return provider::authenticate;
            });
        };
        this.delegate = new JwtIssuerAuthenticationManagerResolver(byIssuer);
    }

    @Override
    public AuthenticationManager resolve(HttpServletRequest request) {
        return delegate.resolve(request);
    }
}
