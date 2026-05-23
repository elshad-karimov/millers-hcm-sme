package az.millers.hcm.organization.domain;

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

@Entity
@Table(name = "structure_version", schema = "organization")
@Getter
@Setter
@NoArgsConstructor
public class StructureVersion {

    @Id
    private UUID id;

    @Column(name = "version_number", nullable = false, unique = true)
    private Integer versionNumber;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VersionStatus status;

    @Column(name = "change_reason", columnDefinition = "text")
    private String changeReason;

    @Column(name = "previous_version_id")
    private UUID previousVersionId;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "activated_at")
    private OffsetDateTime activatedAt;

    @Column(name = "archived_at")
    private OffsetDateTime archivedAt;

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
}
