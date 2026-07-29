package az.millers.hcm.staffing.domain;

import java.math.BigDecimal;
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
 * Single line of a staffing table (M245). One row per approved position
 * group: org unit + position + grade + count + monthly salary.
 *
 * <p>This is a snapshot — once the parent table is APPROVED, edits to
 * the underlying live position do not retroactively change the line.
 */
@Entity
@Table(name = "staffing_table_line", schema = "staffing")
@Getter
@Setter
@NoArgsConstructor
public class StaffingTableLine {

    @Id
    @Column(name = "id")
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "staffing_table_id", nullable = false)
    private UUID staffingTableId;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "org_unit_id")
    private UUID orgUnitId;

    @Column(name = "org_unit_label", length = 200)
    private String orgUnitLabel;

    @Column(name = "position_id")
    private UUID positionId;

    @Column(name = "position_code", length = 64)
    private String positionCode;

    @Column(name = "position_title", nullable = false, length = 200)
    private String positionTitle;

    @Column(name = "grade", length = 32)
    private String grade;

    @Column(name = "approved_headcount", nullable = false)
    private int approvedHeadcount = 1;

    @Column(name = "monthly_salary", precision = 14, scale = 2, nullable = false)
    private BigDecimal monthlySalary = BigDecimal.ZERO;

    @Column(name = "monthly_salary_fund", precision = 14, scale = 2, nullable = false)
    private BigDecimal monthlySalaryFund = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "AZN";

    @Column(name = "notes")
    private String notes;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (currency == null) currency = "AZN";
        // Auto-compute the fund if the caller didn't set it. This is also
        // re-derived in the service on update; the field is kept storable
        // because users may want to override (e.g. mid-month proration).
        if (monthlySalaryFund == null
                || monthlySalaryFund.compareTo(BigDecimal.ZERO) == 0) {
            monthlySalaryFund = monthlySalary.multiply(BigDecimal.valueOf(approvedHeadcount));
        }
    }
}
