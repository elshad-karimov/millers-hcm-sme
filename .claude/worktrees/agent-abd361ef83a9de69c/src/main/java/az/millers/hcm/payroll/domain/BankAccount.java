package az.millers.hcm.payroll.domain;

import java.math.BigDecimal;
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

/**
 * Bank account for salary payment (M74 / P2-06 + P2-07).
 *
 * <p>The 1:1 employee_id unique constraint was dropped in V60. An employee may
 * now hold multiple active accounts with split percentages summing to 100,
 * and at most one row carries {@code is_primary = TRUE} at any time
 * (enforced by the partial unique index {@code uq_bank_one_primary_per_employee}).
 *
 * <p>{@link #iban} and {@link #accountNumber} live under the standard
 * AES-256-GCM encryption. {@code lastChangeWorkflowId} tracks any in-flight
 * approval workflow against the row.
 */
@Entity
@Table(name = "bank_account", schema = "payroll")
@Getter
@Setter
@NoArgsConstructor
public class BankAccount {

    @Id
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "bank_code")
    private String bankCode;

    @Column(name = "bank_name")
    private String bankName;

    /** PII — encrypted at rest (PRD 14.3). */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(length = 500)
    private String iban;

    /** PII — encrypted at rest (PRD 14.3). */
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "account_number", length = 500)
    private String accountNumber;

    /**
     * ISO 9362 SWIFT/BIC code (8 or 11 chars). Required by international
     * transfers + SEPA bank files. M74 / P2-06.
     */
    @Column(name = "swift_bic", length = 11)
    private String swiftBic;

    @Column(nullable = false, length = 3)
    private String currency;

    /**
     * What fraction of the employee's net salary lands in THIS account
     * (M74 / P2-06). Service-layer validation ensures the sum of all
     * active accounts for one employee = 100.
     */
    @Column(name = "salary_split_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal salarySplitPercent = new BigDecimal("100.00");

    /**
     * Whether this is the employee's primary account. The payroll engine
     * falls back to the primary when no explicit split is configured. The
     * partial unique index in V60 enforces "at most one primary per employee".
     */
    @Column(name = "is_primary", nullable = false)
    private boolean primary = true;

    @Column(nullable = false)
    private boolean active = true;

    @Column(columnDefinition = "text")
    private String notes;

    /**
     * Tracks any in-flight bank-change approval workflow against this row.
     * Set when {@code BankAccountService} kicks off an approval; cleared on
     * terminal status.
     */
    @Column(name = "last_change_workflow_id")
    private UUID lastChangeWorkflowId;

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
        if (currency == null) currency = "AZN";
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
