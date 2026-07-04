package az.millers.hcm.corehr.domain;

import java.time.LocalDate;
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
 * Public + religious + company holiday (PRD 8.4 / 8.5 / 8.9 — M38).
 *
 * <p>Feeds three downstream consumers:
 * <ol>
 *   <li>{@code TimesheetGenerator} — a holiday landing on a working
 *       weekday renders as the {@code H} code instead of {@code W}.</li>
 *   <li>{@code LeaveRequestService.computeDays} — when the leave type
 *       sets {@code exclude_holidays}, the day count subtracts holiday
 *       dates.</li>
 *   <li>(future) payroll OT multipliers — holiday-rate overtime.</li>
 * </ol>
 *
 * <p>{@code jurisdiction} lives at the row level so multi-country
 * deployments can coexist in one DB. Default {@code AZ}.
 */
@Entity
@Table(name = "holiday", schema = "core_hr")
@Getter
@Setter
@NoArgsConstructor
public class Holiday {

    @Id
    private UUID id;

    /**
     * ISO-3166 alpha-2 country code (or extended subdivision code).
     * Defaults to {@code AZ} per the seed. Indexed alongside
     * {@code holiday_date} for the hot-path lookup.
     */
    @Column(nullable = false, length = 8)
    private String jurisdiction;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Column(nullable = false, length = 200)
    private String name;

    /**
     * One of {@code PUBLIC} (Labour Code Article 105), {@code RELIGIOUS}
     * (Hijri-calendar feast — Ramazan / Qurban Bayramı), {@code MOURNING}
     * (Article 110 — Black January etc.), or {@code COMPANY}
     * (company-specific closure). CHECK-constrained in the DB.
     */
    @Column(name = "holiday_type", nullable = false, length = 32)
    private String holidayType;

    @Column(nullable = false)
    private boolean recurring;

    @Column(columnDefinition = "text")
    private String notes;

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
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
