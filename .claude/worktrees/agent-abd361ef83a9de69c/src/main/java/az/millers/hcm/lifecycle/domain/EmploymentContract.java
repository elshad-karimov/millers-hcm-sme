package az.millers.hcm.lifecycle.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.common.expiry.ExpiryTrackable;
import az.millers.hcm.corehr.domain.EmploymentType;
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
 * Employment contract — the legal document that binds an employee to the
 * company (M64 / P1-03). One contract per active employment period.
 *
 * <p>Implements {@link ExpiryTrackable} for the {@code end_date} channel, which
 * the M61 {@code ExpiryAlertScheduler} consumes via
 * {@code EmploymentContractEndDateSource}. Probation alerts go through a
 * separate {@code EmploymentContractProbationSource} bean that wraps this
 * entity with a different {@link #getExpiryDate()} projection — keeping the
 * scheduler logic untouched while still surfacing both date channels.
 *
 * <p>Renewal choreography lives in {@code EmploymentContractService} —
 * within one transaction the prior {@code ACTIVE} row flips to {@code RENEWED}
 * and the new one becomes {@code ACTIVE}, so the {@code uq_contract_one_active_per_employee}
 * partial unique index in V52 never trips.
 */
@Entity
@Table(name = "employment_contract", schema = "lifecycle")
@Getter
@Setter
@NoArgsConstructor
public class EmploymentContract implements ExpiryTrackable {

    @Id
    private UUID id;

    @Column(name = "contract_no", nullable = false, unique = true, length = 20)
    private String contractNo;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "contract_type", nullable = false, length = 20)
    private EmploymentType contractType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** Null = open-ended (typical for PERMANENT). */
    @Column(name = "end_date")
    private LocalDate endDate;

    /** Null = no probation period. */
    @Column(name = "probation_end_date")
    private LocalDate probationEndDate;

    @Column(name = "notice_period_days", nullable = false)
    private int noticePeriodDays = 30;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContractStatus status = ContractStatus.DRAFT;

    @Column(name = "signed_by_employee_at")
    private OffsetDateTime signedByEmployeeAt;

    @Column(name = "signed_by_hr_at")
    private OffsetDateTime signedByHrAt;

    @Column(name = "has_confidentiality", nullable = false)
    private boolean hasConfidentiality = false;

    @Column(name = "non_compete_end_date")
    private LocalDate nonCompeteEndDate;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", length = 80)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by", length = 80)
    private String updatedBy;

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

    // ── ExpiryTrackable (end_date channel) ────────────────────────────────────

    @Override
    public LocalDate getExpiryDate() {
        return endDate;
    }

    @Override
    public String getEntityLabel() {
        return "Employment contract";
    }

    @Override
    public String getDisplayName() {
        return contractNo;
    }
}
