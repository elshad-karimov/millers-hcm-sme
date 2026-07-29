package az.millers.hcm.payroll.domain;

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
import org.hibernate.annotations.TenantId;

@Entity
@Table(name = "annual_payroll_summary", schema = "payroll")
@Getter
@Setter
@NoArgsConstructor
public class AnnualPayrollSummary {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "year", nullable = false)
    private int year;

    @Column(name = "total_gross", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalGross = BigDecimal.ZERO;

    @Column(name = "total_income_tax", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalIncomeTax = BigDecimal.ZERO;

    @Column(name = "total_dsmf_employee", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalDsmfEmployee = BigDecimal.ZERO;

    @Column(name = "total_mmi_employee", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalMmiEmployee = BigDecimal.ZERO;

    @Column(name = "total_unemployment", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalUnemplEmployee = BigDecimal.ZERO;

    @Column(name = "total_net", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalNet = BigDecimal.ZERO;

    @Column(name = "total_bonuses", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalBonuses = BigDecimal.ZERO;

    @Column(name = "months_count", nullable = false)
    private int monthsCount;

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
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
