package az.millers.hcm.lifecycle.domain;

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
@Table(name = "offboarding_it_access", schema = "lifecycle")
@Getter @Setter @NoArgsConstructor
public class OffboardingItAccess {

    @Id private UUID id;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "access_type", nullable = false, length = 50)
    private String accessType;

    @Column(name = "display_label", nullable = false, length = 120)
    private String displayLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_status", nullable = false, length = 40)
    private ItAccessStatus accessStatus = ItAccessStatus.PENDING;

    @Column(name = "removal_date")
    private LocalDate removalDate;

    @Column(name = "handled_by", length = 100)
    private String handledBy;

    @Column(length = 200)
    private String reference;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now; updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = OffsetDateTime.now(); }
}
