package az.millers.hcm.common.tenant;

import java.util.Map;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

/**
 * Hibernate discriminator-multitenancy resolver (multi-tenancy Phase 1).
 *
 * <p>Hibernate 6 consults this on every session to decide the current tenant for
 * entities annotated with {@code @TenantId}: it auto-adds {@code WHERE tenant_id
 * = ?} to reads and stamps the value on inserts. Registered via
 * {@link HibernatePropertiesCustomizer} so Spring Boot wires it into the
 * SessionFactory.
 *
 * <p>Phase 1 is behaviour-preserving: no entity carries {@code @TenantId} yet, so
 * nothing is filtered, and {@link #resolveCurrentTenantIdentifier()} returns
 * {@code "default"} (via {@link TenantContext}) — identical to the single-tenant
 * app. Entities are annotated and native-SQL tenant sites converted in Phase 2;
 * the resolver starts returning real tenants once the JWT-issuer filter lands in
 * Phase 3.
 */
@Component
public class TenantIdentifierResolver
        implements CurrentTenantIdentifierResolver<String>, HibernatePropertiesCustomizer {

    @Override
    public String resolveCurrentTenantIdentifier() {
        return TenantContext.current();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }
}
