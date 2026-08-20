package az.millers.hcm.config.plan;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import az.millers.hcm.common.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Enforces per-tenant module entitlement on the API surface.
 *
 * <p>Without this, a plan is decoration: the SPA hides a tile while
 * {@code curl /api/recruitment/vacancies} still answers. Here a module outside
 * the tenant's {@link Plan} — or switched off by the tenant's own admin — returns
 * 403 with a machine-readable reason the frontend turns into an upsell.
 *
 * <p>Runs immediately after {@code TenantResolutionFilter}, which binds
 * {@link TenantContext} inside its own {@code doFilter}, so the tenant is known
 * here. Only {@code /api} paths are considered; unauthenticated requests fall
 * through untouched and are handled by the security chain as before.
 *
 * <p><b>This is packaging, not a security boundary.</b> It sits on top of the
 * role, tenant-isolation, and hierarchy checks and replaces none of them.
 */
public class ModuleAccessFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ModuleAccessFilter.class);

    /**
     * Paths that must answer regardless of module state.
     *
     * <p>{@code /api/public} is anonymous by design. {@code /api/settings} and
     * {@code /api/module-settings} are how an admin sees and undoes module state
     * — gating them could strand a tenant. Note that always-on modules
     * (self-service, platform-admin) are handled by the plan model itself, so
     * this list stays short.
     */
    private static final String[] NEVER_GATED = {
            "/api/public",
            "/api/auth",
    };

    private final ModuleAccessService moduleAccess;
    private final ObjectMapper objectMapper;

    public ModuleAccessFilter(ModuleAccessService moduleAccess, ObjectMapper objectMapper) {
        this.moduleAccess = moduleAccess;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = path(request);
        if (!path.equals("/api") && !path.startsWith("/api/")) {
            return true; // SPA shell, static assets, actuator, swagger
        }
        for (String open : NEVER_GATED) {
            if (path.equals(open) || path.startsWith(open + "/")) {
                return true;
            }
        }
        // CORS preflight carries no credentials — let the CORS layer answer it.
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Optional<HcmModule> owner = ModuleApiMap.resolve(path(request));
        if (owner.isEmpty()) {
            chain.doFilter(request, response); // shared / reference data — never gated
            return;
        }

        HcmModule module = owner.get();
        ModuleAccessService.Reason reason = moduleAccess.check(module);
        if (reason == ModuleAccessService.Reason.ALLOWED) {
            chain.doFilter(request, response);
            return;
        }

        log.debug("Tenant '{}' blocked from {} {} — module {} {}",
                TenantContext.current(), request.getMethod(), path(request), module.key(), reason);
        writeForbidden(response, module, reason);
    }

    private void writeForbidden(HttpServletResponse response, HcmModule module,
                                ModuleAccessService.Reason reason) throws IOException {
        boolean upsell = reason == ModuleAccessService.Reason.NOT_IN_PLAN;
        Plan required = Plan.lowestPlanWith(module);

        // Same envelope as ApiExceptionHandler, plus the fields the SPA needs to
        // tell "buy an upgrade" apart from "your admin turned this off".
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timestamp", Instant.now().toString());
        payload.put("status", HttpStatus.FORBIDDEN.value());
        payload.put("error", HttpStatus.FORBIDDEN.getReasonPhrase());
        payload.put("message", upsell
                ? module.label() + " is not included in your plan. Upgrade to " + required + " to enable it."
                : module.label() + " has been switched off for your organisation. An HR admin can re-enable it in Tenant Settings.");
        payload.put("code", upsell ? "MODULE_NOT_IN_PLAN" : "MODULE_DISABLED");
        payload.put("module", module.key());
        payload.put("moduleLabel", module.label());
        if (upsell) {
            payload.put("requiredPlan", required.name());
        }

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), payload);
    }

    /** Path without the context path, e.g. {@code /api/leave/requests}. */
    private static String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && uri.startsWith(context)) {
            uri = uri.substring(context.length());
        }
        return uri;
    }
}
