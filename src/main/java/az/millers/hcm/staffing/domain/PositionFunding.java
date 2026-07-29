package az.millers.hcm.staffing.domain;

import java.time.LocalDate;
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
import org.hibernate.annotations.TenantId;

/**
 * Singleton funding state for a position (M244 / PRD §21). One row per
 * position; the {@code position_id} is the primary key. Mutable, not
 * versioned — the audit log captures diffs when the status changes.
 */
@Entity
@Table(name = "position_funding", schema = "staffing")
@Getter
@Setter
@NoArgsConstructor
public class PositionFunding {

    @Id
    @Column(name = "position_id")
    private UUID positionId;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private FundingStatus status = FundingStatus.UNFUNDED;

    @Column(name = "funding_source", length = 160)
    private String fundingSource;

    @Column(name = "funding_owner", length = 120)
    private String fundingOwner;

    @Column(name = "funding_expiry")
    private LocalDate fundingExpiry;

    @Column(name = "notes")
    private String notes;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by", length = 120)
    private String updatedBy;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now();
        if (status == null) status = FundingStatus.UNFUNDED;
    }
}
