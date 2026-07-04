package az.millers.hcm.leave.domain;

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
 * M123 — a date range during which leave is blocked or routed through
 * stricter approval. See {@link BlackoutScope} and {@link BlackoutSeverity}.
 */
@Entity
@Table(name = "blackout_window", schema = "leave_mgmt")
@Getter
@Setter
@NoArgsConstructor
public class BlackoutWindow {

    @Id
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private BlackoutScope scope;

    /** Required iff scope = ORG_UNIT. */
    @Column(name = "org_unit_id")
    private UUID orgUnitId;

    /** Required iff scope = LEAVE_TYPE. */
    @Column(name = "leave_type_id")
    private UUID leaveTypeId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private BlackoutSeverity severity = BlackoutSeverity.BLOCK;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", nullable = false, length = 160, updatable = false)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by", length = 160)
    private String updatedBy;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
