package az.millers.hcm.organization.domain;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_type", nullable = false)
    private OrgUnitType unitType;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "head_employee_id")
    private UUID headEmployeeId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

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
