package az.millers.hcm.common.tenant;

import javax.sql.DataSource;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

/**
 * Wraps the application {@link DataSource} in a {@link TenantAwareDataSource} so
 * every JDBC connection is stamped with the current tenant's {@code hcm.tenant}
 * GUC (multi-tenancy — per-tenant numbering).
 *
 * <p>A {@link BeanPostProcessor} is used (rather than declaring the DataSource
 * ourselves) so Spring Boot's DataSource auto-configuration, Flyway and Hibernate
 * wiring are all preserved — we only decorate the finished bean.
 *
 * <p>Trade-off: the container's DataSource bean is now a {@code DelegatingDataSource}
 * rather than a raw {@code HikariDataSource}, so type-based lookups (e.g. Hikari
 * pool metrics) see the wrapper; {@code unwrap(HikariDataSource.class)} still
 * returns the pool. Acceptable for the numbering feature.
 */
@Component
public class TenantDataSourcePostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof DataSource ds && !(bean instanceof TenantAwareDataSource)) {
            return new TenantAwareDataSource(ds);
        }
        return bean;
    }
}
