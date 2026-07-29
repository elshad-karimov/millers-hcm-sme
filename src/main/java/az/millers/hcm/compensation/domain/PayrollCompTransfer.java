package az.millers.hcm.compensation.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

@Entity
@Table(name = "payroll_comp_transfer", schema = "compensation")
@Getter
@Setter
@NoArgsConstructor
public class PayrollCompTransfer {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "source_type", nullable = false, length = 20)
    private String sourceType;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "target_run_id", nullable = false)
    private UUID targetRunId;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "payroll_bonus_id")
    private UUID payrollBonusId;

    @Column(length = 20)
    private String status;

    @Column(name = "transferred_at", nullable = false)
    private OffsetDateTime transferredAt;

    @Column(name = "transferred_by", length = 100)
    private String transferredBy;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (transferredAt == null) transferredAt = OffsetDateTime.now();
        if (status == null) status = "TRANSFERRED";
    }
}
