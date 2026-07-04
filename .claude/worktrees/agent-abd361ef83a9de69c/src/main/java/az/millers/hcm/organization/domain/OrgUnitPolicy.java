package az.millers.hcm.organization.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Per-(org_unit, version) default leave / payroll group assignment
 * (M81). When an employee row's own group_id is NULL, the resolver walks
 * up the org tree from {@code Employee.orgUnitId} until it finds the
 * first non-null override; otherwise falls back to the seeded DEFAULT
 * group.
 */
@Entity
@Table(name = "org_unit_policy", schema = "organization")
@Getter
@Setter
@NoArgsConstructor
public class OrgUnitPolicy {

    @Id
    private UUID id;

    @Column(name = "org_unit_id", nullable = false)
    private UUID orgUnitId;

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    /** Soft FK to leave.leave_group.id — null = no override on this unit. */
    @Column(name = "leave_group_id")
    private UUID leaveGroupId;

    /** Soft FK to payroll.payroll_group.id — null = no override on this unit. */
    @Column(name = "payroll_group_id")
    private UUID payrollGroupId;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", length = 80)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by", length = 80)
    private String updatedBy;

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
