package az.millers.hcm.performance.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** HCM_12 M390 — one recorded measurement of a {@link KpiAssignment} (PRD §7.2). */
@Entity
@Table(name = "kpi_result", schema = "performance")
@Getter
@Setter
@NoArgsConstructor
public class KpiResult {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(name = "assignment_id", nullable = false)
    private UUID assignmentId;

    @Column(name = "period_label", length = 40)
    private String periodLabel;

    @Column(name = "actual_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal actualValue;

    @Column(name = "achievement_percent", precision = 7, scale = 2)
    private BigDecimal achievementPercent;

    @Column(precision = 4, scale = 2)
    private BigDecimal rating;

    @Column(length = 500)
    private String note;

    @Column(name = "recorded_by", length = 80)
    private String recordedBy;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private OffsetDateTime recordedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (recordedAt == null) recordedAt = OffsetDateTime.now();
    }
}
