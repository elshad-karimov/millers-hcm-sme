package az.millers.hcm.compensation.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * M359 — Pay band: salary range (min/mid/max) with optional quartiles.
 * May be linked to a grade or standalone. Effective-dated for historical tracking.
 */
@Entity
@Table(name = "pay_band", schema = "compensation")
@Getter
@Setter
@NoArgsConstructor
public class PayBand {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId = "default";

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    /** Optional FK to staffing.grade. Nullable — a band may be standalone. */
    @Column(name = "grade_id")
    private UUID gradeId;

    @Column(nullable = false, length = 3)
    private String currency = "AZN";

    @Column(name = "min_salary", nullable = false, precision = 14, scale = 2)
    private BigDecimal minSalary;

    @Column(name = "mid_salary", nullable = false, precision = 14, scale = 2)
    private BigDecimal midSalary;

    @Column(name = "max_salary", nullable = false, precision = 14, scale = 2)
    private BigDecimal maxSalary;

    @Column(name = "q1_salary", precision = 14, scale = 2)
    private BigDecimal q1Salary;

    @Column(name = "q3_salary", precision = 14, scale = 2)
    private BigDecimal q3Salary;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = OffsetDateTime.now();
    }
}
