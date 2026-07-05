package az.millers.hcm.businesstrip.domain;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Expense policy validation rule (M454).
 * Validates expense claims against category limits and receipt requirements.
 */
@Entity
@Table(name = "expense_policy", schema = "business_trip")
@Getter
@Setter
@NoArgsConstructor
public class ExpensePolicy {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 80)
    private String tenantId = "default";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ExpenseCategory category;

    @Column(name = "employee_grade", length = 40)
    private String employeeGrade;  // NULL = all grades

    @Column(name = "max_per_transaction", precision = 10, scale = 2)
    private BigDecimal maxPerTransaction;  // NULL = no limit

    @Column(name = "max_daily", precision = 10, scale = 2)
    private BigDecimal maxDaily;  // NULL = no limit

    @Column(name = "receipt_required_above", nullable = false, precision = 10, scale = 2)
    private BigDecimal receiptRequiredAbove = new BigDecimal("20.00");

    @Column(nullable = false)
    private boolean blocked = false;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(nullable = false)
    private boolean active = true;

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
        if (receiptRequiredAbove == null) receiptRequiredAbove = new BigDecimal("20.00");
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
