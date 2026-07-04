package az.millers.hcm.corehr.domain;

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
 * M124 — one append-only row per state-change on an {@link EmployeeAsset}.
 *
 * <p>The previous/new status + employee columns let the SPA timeline
 * render "Sara → John (on 2026-06-12, condition: GOOD)" without
 * joining the audit log.
 */
@Entity
@Table(name = "asset_event", schema = "core_hr")
@Getter
@Setter
@NoArgsConstructor
public class AssetEvent {

    @Id
    private UUID id;

    @Column(name = "asset_id", nullable = false, updatable = false)
    private UUID assetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32, updatable = false)
    private AssetEventType eventType;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private OffsetDateTime occurredAt;

    @Column(nullable = false, length = 160, updatable = false)
    private String actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 20)
    private AssetStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", length = 20)
    private AssetStatus newStatus;

    @Column(name = "previous_employee_id")
    private UUID previousEmployeeId;

    @Column(name = "new_employee_id")
    private UUID newEmployeeId;

    @Column(length = 40)
    private String condition;

    @Column(columnDefinition = "text")
    private String notes;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (occurredAt == null) occurredAt = OffsetDateTime.now();
    }
}
