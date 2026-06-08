package az.millers.hcm.organization.domain;

import java.math.BigDecimal;
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
 * M141 — physical site master (PRD §11).
 *
 * <p>Replaces the free-text {@code location} VARCHAR that was scattered
 * across {@code org_unit}, {@code position}, and {@code employee}.
 * Structured fields drive:
 * <ul>
 *   <li>Geofencing / attendance-device binding</li>
 *   <li>Time-zone-aware shift scheduling</li>
 *   <li>Holiday-calendar override per site (via {@code holidayJurisdiction})</li>
 *   <li>Payroll allowances and statutory reporting per legal entity</li>
 * </ul>
 */
@Entity
@Table(name = "location", schema = "organization")
@Getter
@Setter
@NoArgsConstructor
public class Location {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 60)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "location_type", nullable = false, length = 40)
    private LocationType locationType;

    /** ISO 3166-1 alpha-2. */
    @Column(length = 2)
    private String country;

    @Column(length = 120)
    private String city;

    @Column(length = 120)
    private String region;

    @Column(length = 500)
    private String address;

    /** WGS-84 decimal degrees. Paired with longitude — both null or both set. */
    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    /** IANA timezone ID (e.g. "Asia/Baku", "Europe/London"). */
    @Column(length = 60)
    private String timezone;

    /**
     * Jurisdiction code used to look up public holidays from
     * {@code core_hr.holiday}. When null the system-default is used.
     */
    @Column(name = "holiday_jurisdiction", length = 8)
    private String holidayJurisdiction;

    /** Opaque reference to a future work-calendar master. */
    @Column(name = "work_calendar_code", length = 60)
    private String workCalendarCode;

    /** Soft FK to {@code attendance.shift_group}. */
    @Column(name = "default_shift_group_id")
    private UUID defaultShiftGroupId;

    /** Soft FK to {@code core_hr.employee} — on-site manager. */
    @Column(name = "branch_manager_id")
    private UUID branchManagerId;

    /** Soft FK to {@code organization.legal_entity}. */
    @Column(name = "legal_entity_id")
    private UUID legalEntityId;

    @Column(name = "cost_centre_code", length = 64)
    private String costCentreCode;

    @Column(length = 32)
    private String phone;

    @Column(length = 160)
    private String email;

    @Column(nullable = false)
    private boolean active = true;

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
