package az.millers.hcm.security.auth;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Auth surface after the Keycloak swap (milestone 19).
 *
 * <p>The old {@code POST /api/auth/login} is gone — login happens at Keycloak.
 * This controller now only exposes {@code /me}, which returns the username
 * + roles + token-level metadata extracted from the bearer JWT, so the SPA
 * can render the current user without re-decoding the token itself.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", authentication.getName());
        body.put("roles", roles);

        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            body.put("email", jwt.getClaimAsString("email"));
            body.put("name", jwt.getClaimAsString("name"));
            body.put("issuedAt", jwt.getIssuedAt());
            body.put("expiresAt", jwt.getExpiresAt());
        }
        return body;
    }
}
