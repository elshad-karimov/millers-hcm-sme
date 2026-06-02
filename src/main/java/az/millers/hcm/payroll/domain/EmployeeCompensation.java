package az.millers.hcm.payroll.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.common.history.EffectiveDatedRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Effective-dated monthly base salary for an employee.
 *
 * <p>Implements {@link EffectiveDatedRecord} (M62) so the close-prior-slice
 * mutation goes through the shared default method instead of duplicating the
 * {@code effectiveTo = newFrom - 1} arithmetic.
 */
@Entity
@Table(name = "employee_compensation", schema = "payroll")
@Getter
@Setter
@NoArgsConstructor
public class EmployeeCompensation implements EffectiveDatedRecord {

    @Id
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "monthly_base_salary", nullable = false, precision = 14, scale = 2)
    private BigDecimal monthlyBaseSalary;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by")
    private String createdBy;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (currency == null) currency = "AZN";
    }
}
