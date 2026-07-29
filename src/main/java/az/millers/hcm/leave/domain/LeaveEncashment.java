package az.millers.hcm.leave.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

@Entity
@Table(name = "leave_encashment", schema = "leave_mgmt")
@Getter
@Setter
@NoArgsConstructor
public class LeaveEncashment {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 64, updatable = false)
    private String tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "leave_type_id", nullable = false)
    private UUID leaveTypeId;

    @Column(nullable = false)
    private int year;

    @Column(name = "days_encashed", nullable = false, precision = 8, scale = 2)
    private BigDecimal daysEncashed;

    @Column(name = "daily_rate", nullable = false, precision = 14, scale = 4)
    private BigDecimal dailyRate;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "payroll_bonus_id")
    private UUID payrollBonusId;

    /** PENDING | PAID | REVERSED */
    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_by")
    private String createdBy;

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
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
