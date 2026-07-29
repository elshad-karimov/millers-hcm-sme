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
import org.hibernate.annotations.TenantId;

/** HCM_12 M391 — check-in comment / value-update history row (PRD §8.1 / §8.2). */
@Entity
@Table(name = "okr_check_in", schema = "performance")
@Getter
@Setter
@NoArgsConstructor
public class OkrCheckIn {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 64, updatable = false)
    private String tenantId;

    @Column(name = "objective_id", nullable = false)
    private UUID objectiveId;

    /** Null for objective-level check-in comments. */
    @Column(name = "key_result_id")
    private UUID keyResultId;

    @Column(name = "old_value", precision = 14, scale = 2)
    private BigDecimal oldValue;

    @Column(name = "new_value", precision = 14, scale = 2)
    private BigDecimal newValue;

    @Column(length = 10)
    private String confidence;

    @Column(length = 1000)
    private String comment;

    @Column(name = "recorded_by", length = 80)
    private String recordedBy;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private OffsetDateTime recordedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        recordedAt = OffsetDateTime.now();
    }
}
