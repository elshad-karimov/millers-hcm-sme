package az.millers.hcm.businesstrip.domain;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Expense claim included in a reimbursement batch (M455).
 */
@Entity
@Table(name = "reimbursement_batch_item", schema = "business_trip")
@Getter
@Setter
@NoArgsConstructor
public class ReimbursementBatchItem {

    @Id
    private UUID id;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(name = "expense_claim_id", nullable = false)
    private UUID expenseClaimId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
    }
}
