package az.millers.hcm.corehr.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.common.expiry.ExpiryTrackable;
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

/**
 * M137 — one row per vaccination dose. Sits below {@link EmployeeHealth}
 * because vaccinations evolve over time (boosters, annual flu shot),
 * while the parent row is a current-state snapshot.
 *
 * <p>GDPR Article 9 special-category data — same role gate as
 * {@link EmployeeHealth}. Lot number is critical for safety-recall
 * response; {@code nextDoseDate} drives the M61 ExpiryAlertScheduler
 * via the {@link ExpiryTrackable} interface so HR + the employee get
 * a reminder before the next dose is due.
 */
@Entity
@Table(name = "employee_vaccination", schema = "core_hr")
@Getter
@Setter
@NoArgsConstructor
public class EmployeeVaccination implements ExpiryTrackable {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "vaccine_code", nullable = false, length = 60)
    private String vaccineCode;

    @Column(name = "vaccine_name", nullable = false, length = 200)
    private String vaccineName;

    @Column(name = "administered_date", nullable = false)
    private LocalDate administeredDate;

    @Column(name = "administered_by", length = 160)
    private String administeredBy;

    @Column(name = "lot_number", length = 60)
    private String lotNumber;

    @Column(name = "next_dose_date")
    private LocalDate nextDoseDate;

    @Column(name = "attachment_url", length = 500)
    private String attachmentUrl;

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

    // ── ExpiryTrackable (next_dose_date channel) ──────────────────────────

    @Override
    public LocalDate getExpiryDate() {
        return nextDoseDate;
    }

    @Override public String getEntityLabel() { return "Vaccination"; }

    @Override
    public String getDisplayName() {
        return vaccineName + " — next dose";
    }
}
