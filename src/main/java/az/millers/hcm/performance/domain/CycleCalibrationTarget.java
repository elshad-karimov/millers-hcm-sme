package az.millers.hcm.performance.domain;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/**
 * M121 — per-cycle calibration target distribution. One row per (cycle, band),
 * the percent the org expects to see in that band when reviews close.
 *
 * <p>The actual distribution is computed on the fly from
 * {@link PerformanceReview} rows; this table just carries the goal so HR
 * can compare on the calibration board.
 */
@Entity
@Table(name = "cycle_calibration_target", schema = "performance")
@IdClass(CycleCalibrationTargetId.class)
@Getter
@Setter
@NoArgsConstructor
public class CycleCalibrationTarget {

    @Id
    @Column(name = "cycle_id", nullable = false)
    private UUID cycleId;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 64, updatable = false)
    private String tenantId;

    @Id
    @Column(nullable = false, length = 64)
    private String band;

    @Column(name = "target_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal targetPercent;
}
