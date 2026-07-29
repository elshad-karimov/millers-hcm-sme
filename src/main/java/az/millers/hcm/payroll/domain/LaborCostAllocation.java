package az.millers.hcm.payroll.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/**
 * M485: Labor cost allocation per timesheet day.
 */
@Entity
@Table(name = "labor_cost_allocation", schema = "payroll")
public class LaborCostAllocation {

    @Id
    @GeneratedValue
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 80, updatable = false)
    private String tenantId;

    @Column(name = "timesheet_day_id", nullable = false)
    private UUID timesheetDayId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "cost_center_id")
    private UUID costCenterId;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal hours;

    @Column(name = "hourly_rate", nullable = false, precision = 14, scale = 2)
    private BigDecimal hourlyRate;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 80)
    private String createdBy;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public UUID getTimesheetDayId() { return timesheetDayId; }
    public void setTimesheetDayId(UUID timesheetDayId) { this.timesheetDayId = timesheetDayId; }

    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID employeeId) { this.employeeId = employeeId; }

    public LocalDate getWorkDate() { return workDate; }
    public void setWorkDate(LocalDate workDate) { this.workDate = workDate; }

    public UUID getCostCenterId() { return costCenterId; }
    public void setCostCenterId(UUID costCenterId) { this.costCenterId = costCenterId; }

    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }

    public BigDecimal getHours() { return hours; }
    public void setHours(BigDecimal hours) { this.hours = hours; }

    public BigDecimal getHourlyRate() { return hourlyRate; }
    public void setHourlyRate(BigDecimal hourlyRate) { this.hourlyRate = hourlyRate; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
