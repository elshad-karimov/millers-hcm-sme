package az.millers.hcm.policy.domain;

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

/**
 * M138 — an employee's acknowledgement of one policy version. The
 * UNIQUE(policy_id, employee_id) constraint means an employee can ack
 * a given version exactly once; when a new version is published a
 * fresh row is needed because the FK points at the version row id.
 *
 * <p>{@link #versionAcknowledged} is snapshotted at ack time so audit
 * reports can re-render which version the employee actually saw.
 */
@Entity
@Table(name = "acknowledgement", schema = "policy")
@Getter
@Setter
@NoArgsConstructor
public class PolicyAcknowledgement {

    @Id
    private UUID id;

    @Column(name = "policy_id", nullable = false)
    private UUID policyId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "version_acknowledged", nullable = false)
    private int versionAcknowledged;

    @Column(name = "acknowledged_at", nullable = false, updatable = false)
    private OffsetDateTime acknowledgedAt;

    @Column(name = "acknowledged_ip", length = 64)
    private String acknowledgedIp;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (acknowledgedAt == null) acknowledgedAt = OffsetDateTime.now();
    }
}
