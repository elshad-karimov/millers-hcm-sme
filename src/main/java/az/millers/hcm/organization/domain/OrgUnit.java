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
@Table(name = "org_unit", schema = "organization")
@Getter
@Setter
@NoArgsConstructor
public class OrgUnit {

    @Id
    private UUID id;

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    /** M143 — validated at runtime against {@code organization.org_unit_type}. */
    @Column(name = "unit_type", nullable = false)
    private String unitType;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "head_employee_id")
    private UUID headEmployeeId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /**
     * M140 — soft FK to {@code organization.legal_entity}. Nullable;
     * when set, this org_unit (typically a COMPANY-type root) is the
     * anchor for that legal entity's sub-tree. Descendants inherit by
     * an upward walk in the service layer when the field is null.
     */
    @Column(name = "legal_entity_id")
    private UUID legalEntityId;

    /**
     * M141 — structured FK to {@code organization.location}. Nullable;
     * when set, this unit's physical site is the referenced location
     * (time-zone, GPS, holiday calendar, shift defaults all come from there).
     * Supersedes the legacy free-text {@link #location} column which is
     * kept for backward compatibility but should not be used for new units.
     */
    @Column(name = "location_id")
    private UUID locationId;

    /**
     * M142 — soft FK to {@code core_hr.employee} — the primary HRBP for
     * this unit. Used by the workflow engine for fast single-lookup HRBP
     * routing ({@code resolvesToHrbp} step flag). The full multi-HRBP /
     * backup registry is in {@code organization.hr_partner}.
     */
    @Column(name = "hrbp_id")
    private UUID hrbpId;

    /** M81 — finance / facilities attributes. All optional. */
    @Column(name = "cost_centre_code", length = 64)
    private String costCentreCode;

    @Column(length = 200)
    private String location;

    @Column(name = "contact_email", length = 160)
    private String contactEmail;

    @Column(name = "gl_account", length = 64)
    private String glAccount;

    /** M81 — non-negative integer (DB CHECK). NULL = no budget set. */
    @Column(name = "headcount_budget")
    private Integer headcountBudget;

    /** M81 — defaults to true; flips to false on logical decommissioning. */
    @Column(nullable = false)
    private boolean active = true;

    /**
     * M144 — fine-grained lifecycle state (§26). Derived {@link #active}
     * is kept in sync: {@code false} only for {@code CLOSED}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_state", nullable = false, length = 20)
    private OrgUnitLifecycleState lifecycleState = OrgUnitLifecycleState.ACTIVE;

    @Column(name = "planned_open_date")
    private LocalDate plannedOpenDate;

    @Column(name = "closure_announced_date")
    private LocalDate closureAnnouncedDate;

    @Column(name = "closure_reason", columnDefinition = "text")
    private String closureReason;

    @Column(name = "closed_date")
    private LocalDate closedDate;

    @Column(name = "closed_by", length = 80)
    private String closedBy;

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
