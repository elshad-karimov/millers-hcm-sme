package az.millers.hcm.payroll.domain;

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

@Entity
@Table(name = "bank_account", schema = "payroll")
@Getter
@Setter
@NoArgsConstructor
public class BankAccount {

    @Id
    private UUID id;

    @Column(name = "employee_id", nullable = false, unique = true)
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

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

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
