package az.millers.hcm.payroll.domain;

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
@Table(name = "payroll_run_hold", schema = "payroll")
@Getter
@Setter
@NoArgsConstructor
public class PayrollRunHold {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(length = 500)
    private String reason;

    @Column(name = "held_by", nullable = false, length = 200)
    private String heldBy;

    @Column(name = "held_at", nullable = false, updatable = false)
    private OffsetDateTime heldAt;

    @Column(name = "released_at")
    private OffsetDateTime releasedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (heldAt == null) heldAt = OffsetDateTime.now();
    }
}
