package az.millers.hcm.reporting.custom.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/**
 * M119 — a saved custom-report definition. The interesting bits — fields,
 * filters, sorts — live as JSONB so future enhancements (adding new filter
 * ops, per-field aggregation, computed columns) don't require schema
 * changes.
 *
 * <p>The Java representation of each JSONB column is just {@code List<Map>}
 * — the service materialises these into validated
 * {@link az.millers.hcm.reporting.custom.CustomReportSpec} records on the
 * way out. Storing typed objects in JPA via {@code @JdbcTypeCode(JSON)}
 * has rough edges with Hibernate that the raw-map approach avoids.
 */
@Entity
@Table(name = "custom_report", schema = "reporting")
@Getter
@Setter
@NoArgsConstructor
public class CustomReport {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "source_key", nullable = false, length = 64)
    private String sourceKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fields_json", nullable = false)
    private List<java.util.Map<String, Object>> fieldsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "filters_json", nullable = false)
    private List<java.util.Map<String, Object>> filtersJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sort_json", nullable = false)
    private List<java.util.Map<String, Object>> sortJson;

    @Column(name = "row_limit", nullable = false)
    private int rowLimit = 1_000;

    @Column(nullable = false)
    private boolean shared = false;

    @Column(name = "owner_user", nullable = false, length = 160)
    private String ownerUser;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "last_run_at")
    private OffsetDateTime lastRunAt;

    @Column(name = "last_run_rows")
    private Integer lastRunRows;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
