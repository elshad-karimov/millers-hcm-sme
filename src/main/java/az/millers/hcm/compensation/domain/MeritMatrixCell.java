package az.millers.hcm.compensation.domain;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * M363 — Merit matrix cell: maps (performance_band, range_position) → merit_pct.
 */
@Entity
@Table(name = "merit_matrix_cell", schema = "compensation")
@Getter
@Setter
@NoArgsConstructor
public class MeritMatrixCell {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId = "default";

    @Column(name = "matrix_id", nullable = false)
    private UUID matrixId;

    @Column(name = "performance_band", nullable = false, length = 20)
    private String performanceBand;

    @Column(name = "range_position", nullable = false, length = 10)
    private String rangePosition;

    @Column(name = "merit_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal meritPct;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
    }
}
