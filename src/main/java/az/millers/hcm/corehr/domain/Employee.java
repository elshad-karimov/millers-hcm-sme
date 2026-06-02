package az.millers.hcm.corehr.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.security.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
 * Employee master data &mdash; the single source of truth (PRD 8.1, 16.2).
 *
 * <p>Records are never physically deleted; lifecycle is expressed through
 * {@link EmploymentStatus} (design principle 4).
 */
@Entity
@Table(name = "employee", schema = "core_hr")
@Getter
@Setter
@NoArgsConstructor
public class Employee {

    @Id
    private UUID id;

    @Column(name = "employee_no", nullable = false, unique = true)
    private String employeeNo;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "middle_name")
    private String middleName;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    private String gender;

    /**
     * Marital status (M61 / P1-18). Required for AZ income tax deduction calc;
     * persisted as a CHECK-constrained string to keep enum evolution cheap.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "marital_status", length = 20)
    private MaritalStatus maritalStatus;

    /**
     * Nationality as ISO 3166-1 alpha-2 code (M61 / P1-18). Required for work
     * permit eligibility checks (e.g. AZ residents are exempt from work permit
     * tracking). Stored separately from {@code nationalId} so non-citizen
     * employees with Azerbaijani residency permits are correctly classified.
     */
    @Column(name = "nationality", length = 2)
    private String nationality;

    /**
     * PII — encrypted at rest with AES-256-GCM via {@link EncryptedStringConverter}
     * (PRD 14.3). Plaintext only ever lives in the JVM heap; the DB column holds
     * {@code enc:v1:<base64(IV||ciphertext||tag)>}.
     *
     * <p>Uniqueness is enforced application-side in {@code EmployeeService} —
     * a DB UNIQUE index cannot catch duplicates because per-row AES-GCM IVs
     * mean identical plaintext encrypts differently each time.
     */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "national_id", length = 500)
    private String nationalId;

    private String email;

    private String phone;

    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_status", nullable = false)
    private EmploymentStatus employmentStatus;

    /**
     * Employment type (M61 / P1-09). Lives on the employee — NOT on the
     * position — because part-timers may be paid pro-rata even when their
     * position record is full-time-staffed. {@code PayrollEngine} reads this
     * + {@link #ftePercent} to compute the pay multiplier.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 20)
    private EmploymentType employmentType = EmploymentType.PERMANENT;

    /**
     * Full-Time Equivalent percentage (M61 / P1-09). 100.00 = full-time.
     * Only consumed by {@code PayrollEngine} when {@link #employmentType} is a
     * pro-rata type (PART_TIME / CONTRACTOR / INTERN); for salaried types the
     * value is informational only.
     */
    @Column(name = "fte_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal ftePercent = new BigDecimal("100.00");

    @Column(name = "department_name")
    private String departmentName;

    @Column(name = "position_title")
    private String positionTitle;

    @Column(name = "cost_centre")
    private String costCentre;

    @Column(name = "org_unit_id")
    private UUID orgUnitId;

    @Column(name = "position_id")
    private UUID positionId;

    @Column(name = "manager_id")
    private UUID managerId;

    /**
     * Acting / delegate manager (PRD 9 / 14.9 — M37). When non-null
     * AND today() ∈ [delegateFrom, delegateTo], the workflow engine
     * routes approval tasks normally bound for THIS employee to the
     * delegate instead. Single hop only — no chain walk. A SQL check
     * constraint enforces the all-three-null-or-all-three-set + non-
     * self-delegation invariants.
     */
    @Column(name = "delegate_manager_id")
    private UUID delegateManagerId;

    @Column(name = "delegate_from")
    private java.time.LocalDate delegateFrom;

    @Column(name = "delegate_to")
    private java.time.LocalDate delegateTo;

    /**
     * Optional ABAC org-unit anchor (PRD 14.9). When set, an HR specialist
     * (or other future role) linked to this employee is scoped to the
     * descendants of this org_unit instead of being fully unrestricted.
     */
    @Column(name = "scope_org_unit_id")
    private UUID scopeOrgUnitId;

    /**
     * Auth identifier — maps this employee to the in-memory user account
     * used for {@code /api/self/*} (PRD 11.x self-service). Becomes a soft
     * link to Keycloak's {@code preferred_username} when SSO lands.
     */
    @Column(unique = true)
    private String username;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
