package az.millers.hcm.staffing.domain;

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
 * M259 — Reason master (PRD §22).
 *
 * <p>One row per (category, code) pair. The SPA shows {@code label} but
 * persists {@code code} on the consuming entity so renaming a label
 * never breaks historical breadcrumbs.
 */
@Entity
@Table(name = "reason_master", schema = "staffing")
@Getter
@Setter
@NoArgsConstructor
public class ReasonMaster {

    @Id
    @Column(name = "id")
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 32)
    private ReasonCategory category;

    /** Stable machine code. Used as the persisted value on consuming rows. */
    @Column(name = "code", nullable = false, length = 64)
    private String code;

    /** Human-readable label shown in the SPA Select. */
    @Column(name = "label", nullable = false, length = 200)
    private String label;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder = 100;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

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
