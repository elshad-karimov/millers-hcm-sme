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

/** HCM_12 M392 — one traceable progress-update row per change (PRD §6.4). */
@Entity
@Table(name = "goal_progress_update", schema = "performance")
@Getter
@Setter
@NoArgsConstructor
public class GoalProgressUpdate {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 64, updatable = false)
    private String tenantId;

    @Column(name = "goal_id", nullable = false)
    private UUID goalId;

    @Column(name = "old_progress", precision = 5, scale = 2)
    private BigDecimal oldProgress;

    @Column(name = "new_progress", nullable = false, precision = 5, scale = 2)
    private BigDecimal newProgress;

    @Column(name = "old_status", length = 24)
    private String oldStatus;

    @Column(name = "new_status", length = 24)
    private String newStatus;

    @Column(length = 1000)
    private String note;

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
