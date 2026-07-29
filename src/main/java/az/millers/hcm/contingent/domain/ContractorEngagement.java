package az.millers.hcm.contingent.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

@Entity
@Table(name = "contractor_engagement", schema = "contingent",
    uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "employee_id"}))
public class ContractorEngagement {
    @Id
    @GeneratedValue
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 80, updatable = false)
    private String tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "vendor_agency_id")
    private UUID vendorAgencyId;

    @Column(name = "contract_start", nullable = false)
    private LocalDate contractStart;

    @Column(name = "contract_end")
    private LocalDate contractEnd;

    @Column(precision = 14, scale = 2)
    private BigDecimal rate;

    @Column(name = "rate_unit", length = 20)
    private String rateUnit;

    @Column(name = "po_number", length = 60)
    private String poNumber;

    @Column(name = "tenure_alert_days", nullable = false)
    private Integer tenureAlertDays = 30;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "conversion_date")
    private LocalDate conversionDate;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 80)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", length = 80)
    private String updatedBy;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID employeeId) { this.employeeId = employeeId; }

    public UUID getVendorAgencyId() { return vendorAgencyId; }
    public void setVendorAgencyId(UUID vendorAgencyId) { this.vendorAgencyId = vendorAgencyId; }

    public LocalDate getContractStart() { return contractStart; }
    public void setContractStart(LocalDate contractStart) { this.contractStart = contractStart; }

    public LocalDate getContractEnd() { return contractEnd; }
    public void setContractEnd(LocalDate contractEnd) { this.contractEnd = contractEnd; }

    public BigDecimal getRate() { return rate; }
    public void setRate(BigDecimal rate) { this.rate = rate; }

    public String getRateUnit() { return rateUnit; }
    public void setRateUnit(String rateUnit) { this.rateUnit = rateUnit; }

    public String getPoNumber() { return poNumber; }
    public void setPoNumber(String poNumber) { this.poNumber = poNumber; }

    public Integer getTenureAlertDays() { return tenureAlertDays; }
    public void setTenureAlertDays(Integer tenureAlertDays) { this.tenureAlertDays = tenureAlertDays; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDate getConversionDate() { return conversionDate; }
    public void setConversionDate(LocalDate conversionDate) { this.conversionDate = conversionDate; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
