package az.millers.hcm.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import az.millers.hcm.apikey.security.ApiKeyAuthFilter;
import az.millers.hcm.common.tenant.TenantRegistry;
import az.millers.hcm.security.tenant.TenantAuthenticationResolver;
import az.millers.hcm.security.tenant.TenantResolutionFilter;

/**
 * Stateless OAuth2 / OIDC resource-server security (PRD 14.6).
 *
 * <p>JWTs are issued by Keycloak (`millers-hcm` realm, `hcm-web` public client)
 * and validated here against the realm's signing key. The in-memory user store
 * + the locally-signed JWTs from milestones 1–18 have been retired.
 *
 * <p>Authority mapping: realm roles from the {@code realm_access.roles} claim
 * become {@code ROLE_*} authorities so existing {@code @PreAuthorize("hasRole(...)")}
 * annotations across the codebase keep working unchanged.
 *
 * <p>Principal name: defaults to the {@code preferred_username} claim
 * ("admin" / "hrspec" / "employee") rather than the Keycloak user UUID
 * (`sub`). This preserves the user→employee join via
 * {@code core_hr.employee.username} that landed in milestone 15, and keeps
 * existing audit rows readable.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                            ApiKeyAuthFilter apiKeyAuthFilter,
                                            TenantAuthenticationResolver tenantAuthResolver,
                                            TenantRegistry tenantRegistry) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        // M160 — prometheus scrape endpoint; secured by network/firewall in prod
                        .requestMatchers("/actuator/prometheus").permitAll()
                        // M122 — public pre-boarding portal. The magic-link
                        // token IS the credential; SecurityConfig doesn't
                        // need to know more. The controller maps unknown /
                        // expired tokens to 404.
                        .requestMatchers("/api/public/preboarding/**").permitAll()
                        // M209 — public employee badge / QR verify endpoint.
                        // Returns only non-sensitive fields (name, position,
                        // department, status) — safe for third-party scanners.
                        .requestMatchers("/api/public/employees/**").permitAll()
                        // M139 — public letter verification endpoint. The
                        // 32-char token IS the credential; the controller
                        // returns only non-PII fields (status, request_no,
                        // issued date) so it's safe for third parties.
                        .requestMatchers("/api/public/letters/verify/**").permitAll()
                        // M279 — public career portal: the jobs API serves
                        // only live EXTERNAL postings of non-confidential
                        // requisitions with public-safe fields; /careers/**
                        // is the anonymous server-hosted job board page.
                        .requestMatchers("/api/public/careers/**").permitAll()
                        // "/careers/" listed explicitly: the PathPattern
                        // "/careers/**" does not match the bare trailing-
                        // slash form under Spring Security 6 (M282 smoke
                        // caught a 401 on tracking links).
                        .requestMatchers("/careers", "/careers/", "/careers/**").permitAll()
                        // M170 — Swagger UI + OpenAPI JSON; unauthenticated access is
                        // acceptable because the spec reveals no sensitive data and the
                        // Authorize button in the UI accepts a Keycloak Bearer token.
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml").permitAll()
                        .anyRequest().authenticated())
                // Multi-tenancy Phase 3: a multi-issuer resolver replaces the
                // single fixed JwtDecoder. Each token is routed to a per-realm
                // decoder by its `iss` claim, and only issuers mapped to an
                // active tenant in the registry are trusted.
                .oauth2ResourceServer(oauth2 -> oauth2.authenticationManagerResolver(tenantAuthResolver))
                // M120 — recognise X-API-Key BEFORE the bearer-token filter. A
                // request with both headers prefers the key (cheaper than a
                // JWT decode + validates rate limit too); without the key,
                // the chain falls through to the standard JWT path unchanged.
                .addFilterBefore(apiKeyAuthFilter, BearerTokenAuthenticationFilter.class)
                // Multi-tenancy Phase 3: after the bearer token is authenticated,
                // bind TenantContext from the token's issuer → tenant mapping so
                // Hibernate @TenantId + native-SQL TenantContext.current() scope
                // to the caller's tenant. Cleared in the filter's finally.
                .addFilterAfter(new TenantResolutionFilter(tenantRegistry),
                        BearerTokenAuthenticationFilter.class)
                // Default HTTP security headers (PRD 14.4). HSTS is only meaningful
                // when the app is served behind HTTPS — reverse proxies should
                // pin max-age appropriately for production. CSP locks down API
                // responses; the SPA host serves its own pages with its own CSP.
                .headers(h -> h
                        .frameOptions(f -> f.deny())
                        .contentTypeOptions(c -> {})
                        .referrerPolicy(r -> r.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
                                        .ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'")));
        return http.build();
    }

    /**
     * Maps Keycloak realm roles to Spring {@code ROLE_*} authorities and
     * uses {@code preferred_username} as the principal name.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("preferred_username");
        converter.setJwtGrantedAuthoritiesConverter(SecurityConfig::extractRealmRoles);
        return converter;
    }

    @SuppressWarnings("unchecked")
    private static Collection<GrantedAuthority> extractRealmRoles(Jwt jwt) {
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null && realmAccess.get("roles") instanceof List<?> roles) {
            for (Object role : roles) {
                if (role != null) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                }
            }
        }
        return authorities;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "http://localhost:5180", "http://127.0.0.1:5180",   // Vite dev server
                "http://localhost:5200", "http://127.0.0.1:5200"    // Flutter web dev server
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Retry-After"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
