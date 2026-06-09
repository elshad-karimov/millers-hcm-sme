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

/**
 * Header row for a staffing table version (M245). Lines live in
 * {@link StaffingTableLine}.
 */
@Entity
@Table(name = "staffing_table", schema = "staffing")
@Getter
@Setter
@NoArgsConstructor
public class StaffingTable {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "legal_entity_id", nullable = false)
    private UUID legalEntityId;

    @Column(name = "version_code", nullable = false, length = 64)
    private String versionCode;

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private StaffingTableStatus status = StaffingTableStatus.DRAFT;

    @Column(name = "notes")
    private String notes;

    @Column(name = "submitted_by", length = 120)
    private String submittedBy;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "approved_by", length = 120)
    private String approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "rejected_by", length = 120)
    private String rejectedBy;

    @Column(name = "rejected_at")
    private OffsetDateTime rejectedAt;

    @Column(name = "reject_reason")
    private String rejectReason;

    @Column(name = "archived_at")
    private OffsetDateTime archivedAt;

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
        if (status == null) status = StaffingTableStatus.DRAFT;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
