package az.millers.hcm.config;

import java.io.IOException;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the bundled React SPA (classpath:/static, produced by the web build in
 * the Docker image) and falls back to index.html for client-side routes —
 * deep links such as /home or /payroll/runs — so the browser router takes over.
 *
 * <p>API and actuator paths are never rewritten: they fall through to their
 * controllers (or a normal 404). When no SPA bundle is present (local dev, where
 * Vite serves the SPA on :5180), this resolver simply finds nothing and returns
 * null, so it is a no-op and cannot affect the dev flow or tests.
 */
@Component
public class SpaResourceConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested; // a real static file (index.html, assets/*, brand/*)
                        }
                        // Never hijack the API or actuator — let them resolve normally.
                        if (resourcePath.startsWith("api/") || resourcePath.startsWith("actuator/")) {
                            return null;
                        }
                        // Client-side route → serve the SPA shell (only if one is bundled).
                        Resource index = new ClassPathResource("/static/index.html");
                        return index.exists() ? index : null;
                    }
                });
    }
}
