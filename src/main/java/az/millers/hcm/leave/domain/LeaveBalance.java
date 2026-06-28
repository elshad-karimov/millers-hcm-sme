package az.millers.hcm.leave.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "leave_balance", schema = "leave_mgmt",
        uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "leave_type_id", "year"}))
@Getter
@Setter
@NoArgsConstructor
public class LeaveBalance {

    @Id
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "leave_type_id", nullable = false)
    private UUID leaveTypeId;

    @Column(nullable = false)
    private int year;

    @Column(name = "entitlement_days", nullable = false, precision = 7, scale = 2)
    private BigDecimal entitlementDays = BigDecimal.ZERO;

    @Column(name = "carried_forward_days", nullable = false, precision = 7, scale = 2)
    private BigDecimal carriedForwardDays = BigDecimal.ZERO;

    @Column(name = "adjustment_days", nullable = false, precision = 7, scale = 2)
    private BigDecimal adjustmentDays = BigDecimal.ZERO;

    @Column(name = "used_days", nullable = false, precision = 7, scale = 2)
    private BigDecimal usedDays = BigDecimal.ZERO;

    @Column(name = "reserved_days", nullable = false, precision = 7, scale = 2)
    private BigDecimal reservedDays = BigDecimal.ZERO;

    /** Set at year-end roll-over when the leave type has carryForwardExpiryMonths configured. */
    @Column(name = "carry_forward_expires_at")
    private LocalDate carryForwardExpiresAt;

    @Column(name = "last_recalculated_at", nullable = false)
    private OffsetDateTime lastRecalculatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (lastRecalculatedAt == null) lastRecalculatedAt = OffsetDateTime.now();
    }

    public BigDecimal remaining() {
        return entitlementDays
                .add(carriedForwardDays)
                .add(adjustmentDays)
                .subtract(usedDays)
                .subtract(reservedDays);
    }
}
