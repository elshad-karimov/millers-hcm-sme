package az.millers.hcm.leave.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

import az.millers.hcm.leave.api.dto.SeniorityBracket;

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

    /** M344 — whether remaining balance can be encashed (paid leave types only). */
    @Column(nullable = false)
    private boolean encashable;

    @Column(name = "requires_attachment", nullable = false)
    private boolean requiresAttachment;

    @Column(name = "requires_replacement", nullable = false)
    private boolean requiresReplacement;

    @Column(name = "default_annual_entitlement_days", precision = 6, scale = 2)
    private BigDecimal defaultAnnualEntitlementDays;

    @Column(name = "carry_forward_limit_days", precision = 6, scale = 2)
    private BigDecimal carryForwardLimitDays;

    /** Months from Jan 1 of the new year before carried-forward days expire. Null = no mid-year expiry. */
    @Column(name = "carry_forward_expiry_months")
    private Integer carryForwardExpiryMonths;

    @Column(name = "negative_balance_allowed", nullable = false)
    private boolean negativeBalanceAllowed;

    /** Max overdraft days when negativeBalanceAllowed is true. Null = unlimited. */
    @Column(name = "max_negative_days", precision = 5, scale = 2)
    private BigDecimal maxNegativeDays;

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

    /**
     * Optional seniority-bracket schedule (PRD 8.5.2 — milestone 47).
     * When non-empty, the monthly accrual walker computes each employee's
     * tenure at the start of the target period and picks the matching
     * bracket's {@code annualDays / 12} as the monthly bump, overriding
     * both {@link #monthlyAccrualDays} and {@link #defaultAnnualEntitlementDays} / 12.
     * When empty or {@code null} the existing fallback chain applies unchanged.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "seniority_brackets_json", columnDefinition = "jsonb")
    private List<SeniorityBracket> seniorityBrackets;

    /** M341: How this leave type is measured and requested (DAYS/HALF_DAY/HOURS). */
    @Enumerated(EnumType.STRING)
    @Column(name = "leave_unit", nullable = false, length = 10)
    private LeaveUnit leaveUnit = LeaveUnit.DAYS;

    /** M341: Working hours per day for HOURS-unit types (default 8). */
    @Column(name = "hours_per_day", precision = 4, scale = 2)
    private BigDecimal hoursPerDay = BigDecimal.valueOf(8);

    @Column(name = "category_id")
    private UUID categoryId;

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
