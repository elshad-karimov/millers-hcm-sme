package az.millers.hcm.compbenefits.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "bonus_run_item", schema = "comp_benefits",
        uniqueConstraints = @UniqueConstraint(columnNames = {"run_id", "employee_id"}))
@Getter
@Setter
@NoArgsConstructor
public class BonusRunItem {

    @Id
    private UUID id;

    @Column(name = "item_no", nullable = false, unique = true)
    private String itemNo;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "review_id")
    private UUID reviewId;

    @Column(length = 40)
    private String recommendation;

    @Column(name = "final_rating", precision = 4, scale = 2)
    private BigDecimal finalRating;

    @Column(name = "base_salary", precision = 14, scale = 2)
    private BigDecimal baseSalary;

    @Column(name = "bonus_percent", precision = 5, scale = 2)
    private BigDecimal bonusPercent;

    @Column(name = "bonus_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal bonusAmount;

    @Column(nullable = false, length = 3)
    private String currency = "AZN";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private BonusItemSource source;

    @Column(name = "matrix_rule_id")
    private UUID matrixRuleId;

    @Column(name = "pushed_payroll_bonus_id")
    private UUID pushedPayrollBonusId;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
