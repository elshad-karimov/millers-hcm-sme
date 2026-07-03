package az.millers.hcm.performance.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
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

/** HCM_12 M391 — key result under an objective (PRD §8.2). */
@Entity
@Table(name = "okr_key_result", schema = "performance")
@Getter
@Setter
@NoArgsConstructor
public class OkrKeyResult {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(name = "objective_id", nullable = false)
    private UUID objectiveId;

    @Column(nullable = false, length = 300)
    private String title;

    /** NUMBER | PERCENT | CURRENCY | BOOLEAN */
    @Column(name = "measurement_type", nullable = false, length = 20)
    private String measurementType = "NUMBER";

    @Column(name = "baseline_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal baselineValue = BigDecimal.ZERO;

    @Column(name = "target_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal targetValue;

    @Column(name = "current_value", precision = 14, scale = 2)
    private BigDecimal currentValue;

    /** (current − baseline) / (target − baseline) × 100, clamped 0..100. */
    @Column(name = "progress_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal progressPercent = BigDecimal.ZERO;

    @Column(name = "weight_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal weightPercent = BigDecimal.ZERO;

    /** HIGH | MEDIUM | LOW */
    @Column(length = 10)
    private String confidence;

    @Column(name = "owner_employee_id")
    private UUID ownerEmployeeId;

    @Column(name = "due_date")
    private LocalDate dueDate;

    /** ACTIVE | DONE | AT_RISK | CANCELLED */
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

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
