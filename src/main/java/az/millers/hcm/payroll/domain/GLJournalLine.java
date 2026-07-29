package az.millers.hcm.payroll.domain;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/**
 * M355 — Individual GL journal line (debit or credit).
 */
@Entity
@Table(name = "gl_journal_line", schema = "payroll")
@Getter
@Setter
@NoArgsConstructor
public class GLJournalLine {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "journal_id", nullable = false)
    private UUID journalId;

    @Column(name = "sequence_no", nullable = false)
    private Integer sequenceNo;

    @Column(name = "cost_center_code", length = 100)
    private String costCenterCode;

    @Column(name = "component_kind", length = 20)
    private String componentKind;

    @Column(name = "component_code", length = 50)
    private String componentCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private GLAccountType accountType;

    @Column(name = "gl_account_code", nullable = false, length = 50)
    private String glAccountCode;

    @Column(name = "gl_account_name", nullable = false, length = 200)
    private String glAccountName;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(length = 500)
    private String description;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
    }
}
