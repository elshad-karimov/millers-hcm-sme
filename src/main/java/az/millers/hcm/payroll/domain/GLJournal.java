package az.millers.hcm.payroll.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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

/**
 * M355 — GL journal header generated from a payroll run.
 */
@Entity
@Table(name = "gl_journal", schema = "payroll")
@Getter
@Setter
@NoArgsConstructor
public class GLJournal {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId = "default";

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "journal_date", nullable = false)
    private LocalDate journalDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GLJournalStatus status;

    @Column(name = "total_debit", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalDebit;

    @Column(name = "total_credit", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalCredit;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", length = 200)
    private String createdBy;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = OffsetDateTime.now();
    }
}
