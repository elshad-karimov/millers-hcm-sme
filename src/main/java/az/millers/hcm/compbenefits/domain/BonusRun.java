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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "bonus_run", schema = "comp_benefits")
@Getter
@Setter
@NoArgsConstructor
public class BonusRun {

    @Id
    private UUID id;

    @Column(name = "run_no", nullable = false, unique = true)
    private String runNo;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "cycle_id")
    private UUID cycleId;

    @Column(name = "target_payroll_run_id")
    private UUID targetPayrollRunId;

    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Column(name = "period_month", nullable = false)
    private int periodMonth;

    @Column(nullable = false, length = 3)
    private String currency = "AZN";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BonusRunStatus status;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "employee_count", nullable = false)
    private int employeeCount;

    @Column(name = "generated_at")
    private OffsetDateTime generatedAt;

    @Column(name = "generated_by")
    private String generatedBy;

    @Column(name = "pushed_at")
    private OffsetDateTime pushedAt;

    @Column(name = "pushed_by")
    private String pushedBy;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) status = BonusRunStatus.DRAFT;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
