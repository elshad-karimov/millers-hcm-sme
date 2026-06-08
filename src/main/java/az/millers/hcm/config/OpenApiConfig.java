package az.millers.hcm.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * M170 (PRD §13): OpenAPI 3.1 / Swagger UI configuration.
 *
 * <p>Swagger UI is served at {@code /swagger-ui/index.html}.
 * The raw OpenAPI JSON is available at {@code /v3/api-docs}.
 *
 * <p>All API endpoints require a Bearer JWT issued by Keycloak.  The
 * {@code bearerAuth} security scheme is declared globally so every
 * operation in the Swagger UI shows the Authorize button without
 * needing per-controller annotations.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Millers HCM API")
                        .description("Enterprise Human Capital Management Platform — REST API Reference. " +
                                "Authenticate via the Authorize button using a Keycloak Bearer JWT.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Millers HCM")
                                .email("support@millers.example"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://millers.example")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .name(BEARER_AUTH)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste a Keycloak access token (from POST /realms/millers-hcm/protocol/openid-connect/token).")));
    }
}
