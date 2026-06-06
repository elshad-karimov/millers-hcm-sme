package az.millers.hcm.workflow.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * M126 — append-only row per detected SLA breach. The
 * {@code (instance_id, step_index)} unique constraint at the DB level
 * provides idempotency: the scheduler can race a few times and only
 * one row wins.
 */
@Entity
@Table(name = "sla_breach", schema = "workflow")
@Getter
@Setter
@NoArgsConstructor
public class SlaBreach {

    @Id
    private UUID id;

    @Column(name = "instance_id", nullable = false, updatable = false)
    private UUID instanceId;

    @Column(name = "step_index", nullable = false, updatable = false)
    private int stepIndex;

    @Column(name = "breached_at", nullable = false, updatable = false)
    private OffsetDateTime breachedAt;

    @Column(name = "hours_overdue", nullable = false, updatable = false, precision = 6, scale = 2)
    private BigDecimal hoursOverdue;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_taken", nullable = false, length = 32, updatable = false)
    private EscalationAction actionTaken;

    @Column(name = "notified_target", length = 160, updatable = false)
    private String notifiedTarget;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (breachedAt == null) breachedAt = OffsetDateTime.now();
    }
}
