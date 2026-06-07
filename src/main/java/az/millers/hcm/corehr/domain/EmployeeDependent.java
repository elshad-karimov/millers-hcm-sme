package az.millers.hcm.corehr.domain;

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
 * A family dependent of an employee (M71 / P2-03). Drives benefit enrolment,
 * insurance coverage selection, family-allowance lookups, and (once configured)
 * parental-leave entitlement.
 *
 * <p>{@code nationalId} encrypted at rest via the same converter used on
 * {@code Employee.nationalId} and {@code EmployeeIdentification.documentNumber}.
 */
@Entity
@Table(name = "employee_dependent", schema = "core_hr")
@Getter
@Setter
@NoArgsConstructor
public class EmployeeDependent {

    @Id
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship_type", nullable = false, length = 20)
    private DependentRelationship relationshipType;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "middle_name", length = 100)
    private String middleName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(length = 16)
    private String gender;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "national_id", length = 500)
    private String nationalId;

    @Column(length = 40)
    private String phone;

    @Column(length = 160)
    private String email;

    @Column(name = "insurance_eligible", nullable = false)
    private boolean insuranceEligible = false;

    @Column(name = "benefit_eligible", nullable = false)
    private boolean benefitEligible = false;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    // ── M135 — Section 13 eligibility termination ───────────────────────

    /**
     * Date eligibility ended. When set and ≤ today, downstream
     * benefit-eligibility queries treat this dependent as ineligible.
     * V93 CHECK enforces a {@code (date, reason)} pair — either both or
     * neither.
     */
    @Column(name = "eligibility_end_date")
    private LocalDate eligibilityEndDate;

    /**
     * Why eligibility ended. One of {@link DependentEligibilityEndReason}.
     * Required when {@link #eligibilityEndDate} is set.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "eligibility_end_reason", length = 60)
    private DependentEligibilityEndReason eligibilityEndReason;

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
}
