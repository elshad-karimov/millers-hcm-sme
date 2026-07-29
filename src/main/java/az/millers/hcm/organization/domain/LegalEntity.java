package az.millers.hcm.organization.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

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
import org.hibernate.annotations.TenantId;

/**
 * M140 — a registered company that owns payroll, tax filings, and
 * statutory reporting. Sits above the org-unit hierarchy: one
 * deployment can host many legal entities, and each entity's org tree
 * is anchored on a {@code COMPANY}-type org_unit whose
 * {@code legalEntityId} points back here.
 *
 * <p>Drives payroll bank file generation, statutory deductions, and
 * letter-engine company-seal printing. The encrypted bank account
 * column uses the same AES-256-GCM converter pattern as
 * {@code Employee.nationalId}.
 */
@Entity
@Table(name = "legal_entity", schema = "organization")
@Getter
@Setter
@NoArgsConstructor
public class LegalEntity {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(nullable = false, unique = true, length = 60)
    private String code;

    @Column(nullable = false, length = 240)
    private String name;

    @Column(name = "registration_number", length = 80)
    private String registrationNumber;

    @Column(name = "tax_id", length = 80)
    private String taxId;

    @Column(name = "social_insurance_reg_number", length = 80)
    private String socialInsuranceRegNumber;

    @Column(name = "legal_address", length = 500)
    private String legalAddress;

    /** ISO 3166-1 alpha-2 uppercase. CHECK constraint enforces shape. */
    @Column(length = 2)
    private String country;

    /** ISO 4217 alpha-3 uppercase. */
    @Column(length = 3)
    private String currency;

    @Column(name = "fiscal_calendar", length = 40)
    private String fiscalCalendar;

    @Column(name = "payroll_bank_name", length = 160)
    private String payrollBankName;

    /**
     * Encrypted at rest — same pattern as {@code Employee.bankAccount}.
     * Plaintext lives only in the JVM heap; DB column holds
     * {@code enc:v1:<base64(IV||ciphertext||tag)>}.
     */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "payroll_bank_account", length = 500)
    private String payrollBankAccount;

    /** ISO 9362 SWIFT/BIC (8 or 11 chars). */
    @Column(name = "payroll_bank_swift", length = 11)
    private String payrollBankSwift;

    @Column(name = "default_cost_centre_code", length = 60)
    private String defaultCostCentreCode;

    @Column(name = "chart_of_accounts_ref", length = 120)
    private String chartOfAccountsRef;

    @Column(name = "legal_representative_name", length = 160)
    private String legalRepresentativeName;

    @Column(name = "legal_representative_title", length = 120)
    private String legalRepresentativeTitle;

    @Column(name = "company_seal_url", length = 500)
    private String companySealUrl;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

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
