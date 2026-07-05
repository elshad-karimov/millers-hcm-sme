package az.millers.hcm.businesstrip.domain;

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
 * Per-diem allowance rule for business trips (M452).
 * Matches destination + grade + trip type to calculate meal, lodging, and incidentals allowances.
 * Most specific rule wins: city+grade > city > country+grade > country.
 */
@Entity
@Table(name = "per_diem_rule", schema = "business_trip")
@Getter
@Setter
@NoArgsConstructor
public class PerDiemRule {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 80)
    private String tenantId = "default";

    // Matching dimensions
    @Column(name = "destination_country", nullable = false, length = 80)
    private String destinationCountry;

    @Column(name = "destination_city", length = 120)
    private String destinationCity;  // NULL = country-wide

    @Column(name = "employee_grade", length = 40)
    private String employeeGrade;  // NULL = all grades

    @Enumerated(EnumType.STRING)
    @Column(name = "trip_type", length = 30)
    private TripType tripType;  // NULL = all types

    // Allowances per day
    @Column(name = "meal_allowance", nullable = false, precision = 10, scale = 2)
    private BigDecimal mealAllowance;

    @Column(name = "lodging_allowance", nullable = false, precision = 10, scale = 2)
    private BigDecimal lodgingAllowance;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal incidentals;

    @Column(nullable = false, length = 3)
    private String currency = "AZN";

    // Validity window
    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

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
        if (currency == null) currency = "AZN";
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
