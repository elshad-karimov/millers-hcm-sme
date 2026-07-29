package az.millers.hcm.businesstrip.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
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

/** One line item on an {@link ExpenseClaim} (M104). */
@Entity
@Table(name = "expense_item", schema = "business_trip")
@Getter
@Setter
@NoArgsConstructor
public class ExpenseItem {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "claim_id", nullable = false)
    private UUID claimId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ExpenseCategory category;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "item_date")
    private LocalDate itemDate;

    @Column(name = "receipt_url", columnDefinition = "text")
    private String receiptUrl;

    @Column(name = "policy_flags", length = 500)
    private String policyFlags;  // M454: validation verdict (WARNING | RECEIPT_REQUIRED | BLOCKED)

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
    }
}
