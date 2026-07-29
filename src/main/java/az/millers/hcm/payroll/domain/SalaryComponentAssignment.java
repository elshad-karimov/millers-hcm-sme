package az.millers.hcm.payroll.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/**
 * M349 — Per-employee effective-dated component assignment.
 * Creating a new assignment auto-closes prior open for same (employee, component).
 */
@Entity
@Table(name = "salary_component_assignment", schema = "payroll")
@Getter
@Setter
@NoArgsConstructor
public class SalaryComponentAssignment {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @ManyToOne
    @JoinColumn(name = "component_id", nullable = false)
    private SalaryComponent component;

    /** Overrides component's default calculation for this employee. */
    @Column(name = "amount_override", precision = 12, scale = 2)
    private BigDecimal amountOverride;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = OffsetDateTime.now();
    }
}
