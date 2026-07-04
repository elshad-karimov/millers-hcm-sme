package az.millers.hcm.staffing.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One target line on a workforce plan (M247). */
@Entity
@Table(name = "workforce_plan_line", schema = "staffing")
@Getter
@Setter
@NoArgsConstructor
public class WorkforcePlanLine {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "workforce_plan_id", nullable = false)
    private UUID workforcePlanId;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "org_unit_id")
    private UUID orgUnitId;

    @Column(name = "org_unit_label", length = 200)
    private String orgUnitLabel;

    @Column(name = "job_family", length = 64)
    private String jobFamily;

    @Column(name = "grade", length = 32)
    private String grade;

    @Column(name = "position_title", length = 200)
    private String positionTitle;

    @Column(name = "planned_headcount", nullable = false)
    private int plannedHeadcount = 0;

    @Column(name = "planned_fte", precision = 9, scale = 2, nullable = false)
    private BigDecimal plannedFte = BigDecimal.ZERO;

    @Column(name = "planned_monthly_cost", precision = 14, scale = 2, nullable = false)
    private BigDecimal plannedMonthlyCost = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "AZN";

    @Column(name = "change_type", length = 32)
    private String changeType;

    @Column(name = "target_start_date")
    private LocalDate targetStartDate;

    @Column(name = "justification")
    private String justification;

    @Column(name = "notes")
    private String notes;

    public BigDecimal plannedAnnualCost() {
        return plannedMonthlyCost == null
                ? BigDecimal.ZERO
                : plannedMonthlyCost.multiply(BigDecimal.valueOf(12));
    }

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (currency == null) currency = "AZN";
        // FTE defaults to headcount when caller didn't set one — common
        // case of "every seat is a 1.0 FTE".
        if (plannedFte == null || plannedFte.compareTo(BigDecimal.ZERO) == 0) {
            plannedFte = BigDecimal.valueOf(plannedHeadcount);
        }
    }
}
