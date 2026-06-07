package az.millers.hcm.corehr.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.common.expiry.ExpiryTrackable;
import az.millers.hcm.security.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * GDPR Article 9 special-category data — occupational health information for
 * an employee (M65 / P1-14).
 *
 * <p>One record per employee (enforced by {@code uq_emp_health_one_per_employee}).
 * History (e.g. each individual fitness exam) is out of scope for Phase 1 —
 * this row is the "current state" reference.
 *
 * <p>{@code occupational_health_notes} is AES-256-GCM encrypted in addition
 * to the application-level role gate ({@code EmployeeHealthController} restricts
 * to {@code OCCUPATIONAL_HEALTH / HR_ADMIN / SYSTEM_ADMIN}).
 *
 * <p>Implements {@link ExpiryTrackable} on the {@code nextExamDate} channel so
 * the M61 scheduler reminds HR / occupational health staff before a periodic
 * exam is overdue.
 */
@Entity
@Table(name = "employee_health", schema = "core_hr")
@Getter
@Setter
@NoArgsConstructor
public class EmployeeHealth implements ExpiryTrackable {

    @Id
    private UUID id;

    @Column(name = "employee_id", nullable = false, unique = true)
    private UUID employeeId;

    @Column(name = "fitness_certificate_date")
    private LocalDate fitnessCertificateDate;

    @Column(name = "next_exam_date")
    private LocalDate nextExamDate;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "occupational_health_notes", length = 4000)
    private String occupationalHealthNotes;

    @Column(columnDefinition = "text")
    private String restrictions;

    @Column(name = "is_confidential", nullable = false)
    private boolean confidential = true;

    // ── M137 — Section 18 disability ────────────────────────────────────

    /**
     * Free-form configurable status — NONE / DECLARED / REGISTERED /
     * NOT_DISCLOSED — taxonomy chosen per deployment. Spec wording is
     * "if legally allowed and needed".
     */
    @Column(name = "disability_status", length = 40)
    private String disabilityStatus;

    /**
     * Statutory severity 0..100 (AZ + other jurisdictions). Drives
     * accommodation + tax-benefit eligibility math when present.
     */
    @Column(name = "disability_percent")
    private Integer disabilityPercent;

    /**
     * Free-form details. Encrypted at rest via the same AES-256-GCM
     * converter as {@link #occupationalHealthNotes}; same role gate
     * still applies at the controller layer.
     */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "disability_note", length = 4000)
    private String disabilityNote;

    /**
     * Workplace accommodations the employee needs. Encrypted at rest.
     */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "accommodations_note", length = 4000)
    private String accommodationsNote;

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

    // ── ExpiryTrackable (next_exam_date channel) ──────────────────────────────

    @Override
    public LocalDate getExpiryDate() {
        return nextExamDate;
    }

    @Override public String getEntityLabel() { return "Medical exam"; }

    @Override
    public String getDisplayName() {
        return "Periodic medical examination";
    }
}
