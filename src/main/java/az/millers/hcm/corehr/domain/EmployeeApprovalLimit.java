package az.millers.hcm.corehr.domain;

import java.math.BigDecimal;
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
 * M261 / PRD §27 — Employee approval limit.
 *
 * <p>Effective-dated row carrying the max monetary authority an employee
 * has for a given approval type. Rows are created automatically by the
 * M248 position-profile auto-grant when a manager is hired into a
 * position with an APPROVAL_LIMIT profile item, and effective-dated
 * out on termination / occupancy end.
 */
@Entity
@Table(name = "employee_approval_limit", schema = "core_hr")
@Getter
@Setter
@NoArgsConstructor
public class EmployeeApprovalLimit {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "limit_type", nullable = false, length = 32)
    private ApprovalLimitType limitType;

    @Column(name = "max_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal maxAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "AZN";

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    /** NULL = currently active. */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    /** PROFILE_GRANT / MANUAL — where the row came from. */
    @Column(name = "source", nullable = false, length = 32)
    private String source = "MANUAL";

    /** Back-link to the M248 PositionProfileGrant row when source=PROFILE_GRANT. */
    @Column(name = "source_grant_id")
    private UUID sourceGrantId;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

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
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
