package az.millers.hcm.performance.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import az.millers.hcm.performance.api.dto.SuccessionGridDtos.Readiness;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Explicit named-successor record (M103).
 *
 * <p>Complements the M92 9-box bucket placement with a durable, auditable
 * record that HR can present to auditors: "Employee X has been formally
 * designated as the Ready-Now successor for Position Y."
 */
@Entity
@Table(name = "succession_nomination", schema = "performance")
@Getter
@Setter
@NoArgsConstructor
public class SuccessionNomination {

    @Id
    private UUID id;

    @Column(name = "position_id", nullable = false)
    private UUID positionId;

    @Column(name = "nominee_employee_id", nullable = false)
    private UUID nomineeEmployeeId;

    @Column(name = "nominated_by", length = 80)
    private String nominatedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "readiness_tier", nullable = false, length = 20)
    private Readiness readinessTier;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "cancellation_reason", columnDefinition = "text")
    private String cancellationReason;

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
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public boolean isActive() {
        return cancelledAt == null;
    }
}
