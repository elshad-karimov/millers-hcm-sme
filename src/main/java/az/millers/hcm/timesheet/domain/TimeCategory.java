package az.millers.hcm.timesheet.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/**
 * A configurable quantity a timesheet day can carry — "Offshore Hours",
 * "Meal Allowance", "Quayside Nightshift Hours".
 *
 * <p>Catalog-driven so a new pay-relevant quantity is a row rather than a
 * migration, and so payroll can later bind a salary component to a stable
 * {@link #code} instead of hardcoding one customer's spreadsheet columns.
 *
 * <p>Carries no rate and no amount. What a category is worth is a payroll
 * question, answered in a later slice.
 */
@Entity
@Table(name = "time_category", schema = "timesheet")
@Getter
@Setter
@NoArgsConstructor
public class TimeCategory {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    /** Stable key referenced by payroll — never rename in place. */
    @Column(nullable = false, length = 60)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    /** HOURS or DAYS. */
    @Column(nullable = false, length = 10)
    private String unit;

    /** CSV of {@link WorkType} names this category applies to; blank = any. */
    @Column(name = "applies_to", nullable = false, length = 300)
    private String appliesTo = "";

    /** True when the system computes it and the employee may not type it. */
    @Column(nullable = false)
    private boolean derived;

    /** EMPLOYEE / HOLIDAY_CALENDAR / SHIFT_SCHEDULE / LEAVE. */
    @Column(nullable = false, length = 20)
    private String source = "EMPLOYEE";

    /** Validation ceiling for a single calendar day. */
    @Column(name = "max_per_day", nullable = false, precision = 6, scale = 2)
    private BigDecimal maxPerDay = BigDecimal.valueOf(24);

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 100;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    /** Work types this category may be entered against; empty set = any. */
    public Set<WorkType> appliesToTypes() {
        if (appliesTo == null || appliesTo.isBlank()) return Set.of();
        Set<WorkType> out = new LinkedHashSet<>();
        Arrays.stream(appliesTo.split(","))
                .map(WorkType::parse)
                .filter(java.util.Objects::nonNull)
                .forEach(out::add);
        return out;
    }

    /** Whether this category is enterable against the given work type. */
    public boolean appliesTo(WorkType type) {
        Set<WorkType> allowed = appliesToTypes();
        return allowed.isEmpty() || (type != null && allowed.contains(type));
    }

    public boolean isDays() {
        return "DAYS".equalsIgnoreCase(unit);
    }

    /** True for hour-denominated categories — the only ones that sum to a day's hours. */
    public boolean isHours() {
        return "HOURS".equalsIgnoreCase(unit);
    }

    /** V322: actual overtime is captured in minutes and rounded into hours. */
    public boolean isMinutes() {
        return "MINUTES".equalsIgnoreCase(unit);
    }
}
