package az.millers.hcm.staffing.domain;

import java.math.BigDecimal;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One grant created on occupancy from a {@link PositionProfileItem}
 * (M250 / Phase F.2). Snapshot of the profile item at grant time so
 * editing the position profile later doesn't change historical grants.
 */
@Entity
@Table(name = "position_profile_grant", schema = "staffing")
@Getter
@Setter
@NoArgsConstructor
public class PositionProfileGrant {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "occupancy_id", nullable = false)
    private UUID occupancyId;

    @Column(name = "profile_item_id")
    private UUID profileItemId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "position_id", nullable = false)
    private UUID positionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 32)
    private ProfileItemType itemType;

    @Column(name = "label", nullable = false, length = 200)
    private String label;

    @Column(name = "value_amount", precision = 14, scale = 2)
    private BigDecimal valueAmount;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "reference_code", length = 120)
    private String referenceCode;

    @Column(name = "notes")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private GrantStatus status = GrantStatus.PENDING;

    @Column(name = "granted_at")
    private OffsetDateTime grantedAt;

    @Column(name = "granted_by", length = 120)
    private String grantedBy;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "revoked_by", length = 120)
    private String revokedBy;

    @Column(name = "revoke_reason")
    private String revokeReason;

    @Column(name = "failure_reason")
    private String failureReason;

    // M251 — Phase F.3: back-link to the downstream row this grant
    // created (e.g. comp_benefits.employee_allowance). Reserved as a
    // generic UUID + type discriminator so later phases (training
    // enrolment, equipment issuance) can reuse the same column without
    // another migration.
    @Column(name = "downstream_entity_id")
    private UUID downstreamEntityId;

    @Column(name = "downstream_entity_type", length = 32)
    private String downstreamEntityType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by", length = 120)
    private String updatedBy;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) status = GrantStatus.PENDING;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
