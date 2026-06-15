package az.millers.hcm.recruitment.domain;

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
 * M295 — Recruitment PRD Phase F: an employee's referral of a candidate,
 * with a qualifying clock and a payroll-bonus payout once qualified.
 */
@Entity
@Table(name = "referral", schema = "recruitment")
@Getter
@Setter
@NoArgsConstructor
public class Referral {

    @Id
    private UUID id;

    @Column(name = "referral_no", nullable = false, unique = true, length = 20)
    private String referralNo;

    /** Soft FK to {@code core_hr.employee}. */
    @Column(name = "referrer_employee_id", nullable = false)
    private UUID referrerEmployeeId;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Column(name = "vacancy_id")
    private UUID vacancyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReferralStatus status = ReferralStatus.SUBMITTED;

    @Column(name = "bonus_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal bonusAmount;

    @Column(nullable = false, length = 3)
    private String currency = "AZN";

    @Column(name = "qualifying_days", nullable = false)
    private int qualifyingDays = 90;

    @Column(name = "hired_at")
    private OffsetDateTime hiredAt;

    @Column(name = "qualified_at")
    private OffsetDateTime qualifiedAt;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(name = "payroll_run_id")
    private UUID payrollRunId;

    @Column(name = "payroll_bonus_id")
    private UUID payrollBonusId;

    @Column(length = 1000)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @Column(name = "updated_by", length = 120)
    private String updatedBy;

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
