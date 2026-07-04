package az.millers.hcm.leave.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import az.millers.hcm.leave.api.dto.SeniorityBracket;

/**
 * Per-({@link LeaveGroup}, {@link LeaveType}) override of an entitlement
 * attribute (M66 / P1-08).
 *
 * <p>Override semantics: each nullable column means "no override for this
 * attribute — fall through to the LeaveType's same-named field". A group
 * can therefore override just the annual-days budget while inheriting the
 * type's seniority schedule, or vice versa.
 *
 * <p>The accrual walker resolves the chain in {@code LeaveAccrualService.resolveAccrualSource}
 * — this entity carries data, not behaviour, so its responsibilities stay narrow.
 */
@Entity
@Table(name = "leave_group_entitlement", schema = "leave_mgmt")
@Getter
@Setter
@NoArgsConstructor
public class LeaveGroupEntitlement {

    @Id
    private UUID id;

    @Column(name = "leave_group_id", nullable = false)
    private UUID leaveGroupId;

    @Column(name = "leave_type_id", nullable = false)
    private UUID leaveTypeId;

    @Column(name = "annual_entitlement_days", precision = 6, scale = 2)
    private BigDecimal annualEntitlementDays;

    @Column(name = "monthly_accrual_days", precision = 6, scale = 2)
    private BigDecimal monthlyAccrualDays;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "seniority_brackets_json", columnDefinition = "jsonb")
    private List<SeniorityBracket> seniorityBrackets;

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
