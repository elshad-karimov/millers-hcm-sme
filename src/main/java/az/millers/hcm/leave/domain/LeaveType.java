package az.millers.hcm.leave.domain;

import java.math.BigDecimal;
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

@Entity
@Table(name = "leave_type", schema = "leave_mgmt")
@Getter
@Setter
@NoArgsConstructor
public class LeaveType {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private boolean paid;

    @Column(name = "requires_attachment", nullable = false)
    private boolean requiresAttachment;

    @Column(name = "requires_replacement", nullable = false)
    private boolean requiresReplacement;

    @Column(name = "default_annual_entitlement_days", precision = 6, scale = 2)
    private BigDecimal defaultAnnualEntitlementDays;

    @Column(name = "carry_forward_limit_days", precision = 6, scale = 2)
    private BigDecimal carryForwardLimitDays;

    /**
     * When {@code true}, {@code LeaveAccrualService} credits
     * {@link #monthlyAccrualDays} (or default/12 fallback) on the 1st of
     * every month. When {@code false}, the type remains one-shot —
     * entitlement is granted in full when the balance is first
     * materialised. See migration V30 (PRD 8.5.2 — milestone 34).
     */
    @Column(name = "accrues_monthly", nullable = false)
    private boolean accruesMonthly;

    /**
     * Explicit per-month bump in days. {@code NULL} falls back to
     * {@link #defaultAnnualEntitlementDays} / 12.
     */
    @Column(name = "monthly_accrual_days", precision = 6, scale = 2)
    private BigDecimal monthlyAccrualDays;

    @Column(name = "max_consecutive_days")
    private Integer maxConsecutiveDays;

    @Column(name = "exclude_weekends", nullable = false)
    private boolean excludeWeekends;

    @Column(name = "exclude_holidays", nullable = false)
    private boolean excludeHolidays;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
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
