package az.millers.hcm.common.tenant;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Stamps the current tenant onto each JDBC connection via the Postgres
 * {@code hcm.tenant} GUC (multi-tenancy — per-tenant numbering).
 *
 * <p>Hibernate's {@code @TenantId} scopes JPA reads/writes, but native SQL
 * (repository {@code nativeQuery} + {@code JdbcTemplate}) bypasses it. The
 * per-tenant numbering function {@code config.next_tenant_seq(seq)} reads
 * {@code current_setting('hcm.tenant')} to pick the caller's counter — so every
 * connection handed out must carry the current tenant. Setting it on acquisition
 * (a cheap in-memory {@code set_config}) covers the connection Spring binds to
 * the request's transaction, on which the numbering call then runs.
 *
 * <p>Failures are swallowed: numbering then falls back to the 'default' counter,
 * which is still unique — never a hard request failure.
 */
public class TenantAwareDataSource extends DelegatingDataSource {

    private static final Logger log = LoggerFactory.getLogger(TenantAwareDataSource.class);

    public TenantAwareDataSource(DataSource target) {
        super(target);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return stampTenant(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return stampTenant(super.getConnection(username, password));
    }

    private Connection stampTenant(Connection c) {
        try (PreparedStatement ps = c.prepareStatement("SELECT set_config('hcm.tenant', ?, false)")) {
            ps.setString(1, TenantContext.current());
            ps.execute();
        } catch (SQLException e) {
            log.debug("Could not set hcm.tenant GUC on connection: {}", e.getMessage());
        }
        return c;
    }
}
