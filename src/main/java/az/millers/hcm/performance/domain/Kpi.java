package az.millers.hcm.performance.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

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

/** HCM_12 M390 — reusable KPI library entry (PRD §5.5). */
@Entity
@Table(name = "kpi", schema = "performance")
@Getter
@Setter
@NoArgsConstructor
public class Kpi {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 64, updatable = false)
    private String tenantId;

    @Column(name = "kpi_code", nullable = false, length = 40)
    private String kpiCode;

    @Column(name = "kpi_name", nullable = false, length = 200)
    private String kpiName;

    @Column(length = 60)
    private String category;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "measurement_unit", length = 40)
    private String measurementUnit;

    @Column(name = "default_target", precision = 14, scale = 2)
    private BigDecimal defaultTarget;

    @Column(name = "min_threshold", precision = 14, scale = 2)
    private BigDecimal minThreshold;

    @Column(name = "max_threshold", precision = 14, scale = 2)
    private BigDecimal maxThreshold;

    /** MONTHLY | QUARTERLY | SEMI_ANNUAL | ANNUAL */
    @Column(nullable = false, length = 20)
    private String frequency = "ANNUAL";

    /** LINEAR | THRESHOLD (§7.4) */
    @Column(name = "scoring_model", nullable = false, length = 20)
    private String scoringModel = "LINEAR";

    /** MANUAL | MANAGER | EMPLOYEE | IMPORT | EXTERNAL (§7.3 — external is a seam) */
    @Column(name = "data_source", nullable = false, length = 30)
    private String dataSource = "MANUAL";

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_by", length = 80)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = OffsetDateTime.now(); }
}
