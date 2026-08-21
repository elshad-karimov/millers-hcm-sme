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
import org.hibernate.annotations.TenantId;

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

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

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

    // ── M132 — Section 1 cosmetic completion ────────────────────────────
    //
    // Five identity fields the Employee Management spec §1 lists that
    // never made it into the codebase. Each is plain VARCHAR — they're
    // PII but not "special category" GDPR data (religion possibly is,
    // depending on jurisdiction; collection should be gated by config).


    /**
     * Birth place, split into country / city / address by V329. The single
     * free-text field it replaced could not be filtered or grouped; the old
     * value was copied into {@code birthAddress} by that migration.
     */
    @Column(name = "birth_country", length = 2)
    private String birthCountry;

    /** Suggested from a list, but free text is accepted — people are born anywhere. */
    @Column(name = "birth_city", length = 120)
    private String birthCity;

    /** The remainder: village, district, street. */
    @Column(name = "birth_address", length = 255)
    private String birthAddress;

    /**
     * ISO 639-1 alpha-2. No longer on the employee form — it was removed as a
     * field HR does not maintain — but kept on the record because
     * LetterRequestService still resolves the letter template language from it,
     * and dropping it would silently change which template a letter uses.
     */
    @Column(name = "native_language", length = 2)
    private String nativeLanguage;




    // ── M133 — Section 3 contact completion ────────────────────────────
    //
    // `email` + `phone` are personal (V1). The five fields below
    // separate the work side so payslip / letter / directory rendering
    // can pick the right address for the audience.

    /** Personal alternative number — NOT the emergency contact's alt. */
    @Column(name = "alt_phone", length = 32)
    private String altPhone;

    /** Business email — typically @company.com. */
    @Column(name = "work_email", length = 160)
    private String workEmail;

    /** Main office line. */
    @Column(name = "work_phone", length = 32)
    private String workPhone;



    // ── M134 — Section 4 employment completion ─────────────────────────

    /**
     * Configurable bucket distinct from {@link #employmentType}. Real
     * HCMs use this for white-collar / blue-collar, salaried / hourly,
     * executive / manager / IC, or local / expat — each deployment
     * picks its taxonomy. Free-form VARCHAR per the spec wording
     * ("configurable").
     */
    @Column(name = "employee_category", length = 60)
    private String employeeCategory;

    /**
     * Tenure anchor distinct from {@link #hireDate}. When set, leave
     * accrual + benefits eligibility consult this instead. Lets a
     * rehire carry their original tenure forward; a group-company
     * transferee can keep accumulated years too. CHECK ensures this is
     * never in the future.
     */
    @Column(name = "seniority_date")
    private java.time.LocalDate seniorityDate;

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

    /**
     * VÖEN — Azerbaijan individual taxpayer identification number (PRD §8.1.1).
     * Used in the ABB corporate salary-disbursement bank file (§8.9.6).
     * Stored encrypted at rest; same AES-256-GCM scheme as {@link #nationalId}.
     */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "tax_id", length = 500)
    private String taxId;

    /**
     * DSMF social-insurance ID (PRD §8.1.1).
     * Stored encrypted at rest; same AES-256-GCM scheme as {@link #nationalId}.
     */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "social_insurance_id", length = 500)
    private String socialInsuranceId;

    private String email;

    private String phone;

    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;

    /**
     * M471 — Work authorization expiry (visa/work permit). Used for
     * compliance tracking; non-citizens without valid authorization
     * may not be scheduled or paid (warning-only in current implementation).
     */
    @Column(name = "work_authorized_until")
    private LocalDate workAuthorizedUntil;

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
     * M141 — FK to {@code organization.location}. The physical site
     * where this employee primarily works. Drives geofencing, shift
     * defaults, payroll location allowances, and on-site attendance rules.
     */
    @Column(name = "work_location_id")
    private UUID workLocationId;

    /**
     * Logical leave-policy group (M66 / P1-08). Resolved by
     * {@code LeaveAccrualService} to look up per-group entitlement overrides
     * before falling back to the {@code LeaveType} defaults. {@code NULL}
     * means "use the default group" (the one with {@code is_default = true}).
     */
    @Column(name = "leave_group_id")
    private UUID leaveGroupId;

    /**
     * Payroll group (M75 / P2-19). Routes the employee through a specific
     * pay calendar + bank file format + currency. {@code NULL} falls
     * through to the default payroll group.
     */
    @Column(name = "payroll_group_id")
    private UUID payrollGroupId;

    /**
     * Matrix / dotted-line manager (M75 / P2-21). Distinct from
     * {@link #managerId} (the line manager driving approval workflows) and
     * {@link #delegateManagerId} (the temporary stand-in for the line
     * manager). The matrix manager is the secondary reporting line for
     * cross-functional roles — informational only, not consumed by the
     * workflow engine.
     */
    @Column(name = "matrix_manager_id")
    private UUID matrixManagerId;

    /**
     * M146 / §9 — functional manager: the manager in a functional or project
     * line of authority. Distinct from {@link #managerId} (line manager driving
     * approval workflows), {@link #matrixManagerId} (dotted-line, informational)
     * and {@link #delegateManagerId} (temporary stand-in). Informational only —
     * not consumed by the workflow engine.
     */
    @Column(name = "functional_manager_id")
    private UUID functionalManagerId;

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

    /**
     * M78 / P2-15 — rehire eligibility. Set to false by HR when terminating
     * for misconduct or similar to block accidental rehire. Default true so
     * the historical population stays open.
     */
    @Column(name = "rehire_eligible", nullable = false)
    private boolean rehireEligible = true;

    /**
     * M78 / P2-15 — soft self-FK to the prior employee row when this row
     * was created via the rehire flow. Lets the UI render "rehired from
     * EMP-00042" and keeps tenure-since calculations honest.
     */
    @Column(name = "previous_employee_id")
    private UUID previousEmployeeId;

    /** M78 / P2-15 — captured at rehire time, audited. */
    @Column(name = "rehire_reason", columnDefinition = "text")
    private String rehireReason;

    // ── M150 — workforce-register master data ───────────────────────────────
    // Mapped from the customer's live personnel register. Everything already
    // owned by another module (salary, allowances, leave entitlement, contract
    // dates, termination) is deliberately absent here — see V325.

    /**
     * The customer's GHRS / legacy-HRIS number. Distinct from
     * {@code employeeNo}, which this platform generates; both are needed to
     * reconcile against the source system after migration. Unique per tenant.
     */
    @Column(name = "external_hr_id", length = 40)
    private String externalHrId;

    /**
     * Full legal name in the local script and order — e.g.
     * "ABBASLI Abbas Elxan oğlu". Azerbaijani labour contracts and state
     * filings require this form; it cannot be reassembled from
     * first/last/middle because the patronymic suffix is not derivable.
     */
    @Column(name = "full_name_local", length = 300)
    private String fullNameLocal;

    /** Recruitment channel the hire came through (agency, referral, direct, …). */
    @Column(name = "source_of_hire", length = 80)
    private String sourceOfHire;

    /** Job title in the local language — used on contracts and orders. */
    @Column(name = "position_title_local", length = 300)
    private String positionTitleLocal;

    /**
     * State occupational classifier entry ("Məşğulluq təsnifatı") — mandatory
     * on Azerbaijani labour-contract filings.
     */
    @Column(name = "occupation_classification", length = 160)
    private String occupationClassification;

    /**
     * Internal grade bucket (Specialist / Manager / Worker / Director).
     * Free-form: each tenant runs its own taxonomy.
     */
    @Column(name = "position_classification", length = 60)
    private String positionClassification;

    /** Onshore / offshore / quayside / hybrid — selects rate + schedule pattern. */
    @Enumerated(EnumType.STRING)
    @Column(name = "work_type", length = 20)
    private EmployeeWorkType workType;

    /**
     * Cost-bearing project the employee is charged to, as labelled in the
     * personnel register. The timesheet project remains the authoritative
     * booking dimension — this is the master-data label.
     */
    @Column(name = "project_name", length = 200)
    private String projectName;

    /**
     * Total professional experience in years. Feeds seniority-based leave
     * entitlement (Art. 116.1) and grading reviews. Bounded 0..70 by the DB.
     */
    @Column(name = "professional_experience_years", precision = 4, scale = 1)
    private BigDecimal professionalExperienceYears;

    /**
     * Whether a signed job description is on file — "Provided",
     * "Waiting from &lt;party&gt;", etc. Compliance checklists key off this.
     */
    @Column(name = "job_description_status", length = 120)
    private String jobDescriptionStatus;

    /**
     * Approves this employee's timesheets when that is not the line manager.
     * Null = fall back to {@code managerId}.
     */
    @Column(name = "timesheet_approver_id")
    private UUID timesheetApproverId;

    /** Approves this employee's expense claims. Null = fall back to {@code managerId}. */
    @Column(name = "expense_approver_id")
    private UUID expenseApproverId;

    /**
     * HR-side verifier who checks the timesheet after the approver signs it
     * and before payroll picks it up.
     */
    @Column(name = "hr_timesheet_verifier_id")
    private UUID hrTimesheetVerifierId;

    /**
     * Agreed work pattern as worded in the contract — e.g.
     * "5 days/40 hrs per week/Random Offshore trip". Reproduced verbatim on
     * contracts and orders; the attendance module still computes actual time.
     */
    @Column(name = "work_schedule_text", length = 200)
    private String workScheduleText;

    /** Agreed daily working hours as worded in the contract — e.g. "8:00 - 17:00". */
    @Column(name = "work_time_text", length = 60)
    private String workTimeText;

    /** Agreed unpaid break as worded in the contract — e.g. "13:00 - 14:00". */
    @Column(name = "lunch_time_text", length = 60)
    private String lunchTimeText;

    /** Offshore rotation pattern when it differs — e.g. "12 hrs p/d". */
    @Column(name = "offshore_work_schedule_text", length = 120)
    private String offshoreWorkScheduleText;

    /**
     * Summarized working-time accounting period (Art. 62) — e.g. "1 mnth",
     * or a fixed-date scheme such as "EoC or FD".
     */
    @Column(name = "summarized_period_method", length = 80)
    private String summarizedPeriodMethod;

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
