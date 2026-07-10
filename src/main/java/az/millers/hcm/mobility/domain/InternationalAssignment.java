package az.millers.hcm.mobility.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * M487: International assignment (tracking only).
 */
@Entity
@Table(name = "international_assignment", schema = "mobility")
public class InternationalAssignment {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 80)
    private String tenantId;

    @Column(name = "assignment_no", nullable = false, unique = true, length = 20)
    private String assignmentNo;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "host_country", nullable = false, length = 80)
    private String hostCountry;

    @Column(name = "host_city", length = 120)
    private String hostCity;

    @Column(name = "host_entity", length = 200)
    private String hostEntity;

    @Column(columnDefinition = "TEXT")
    private String purpose;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(nullable = false, length = 20)
    private String status = "PLANNED";

    @Column(name = "visa_type", length = 60)
    private String visaType;

    @Column(name = "visa_expiry")
    private LocalDate visaExpiry;

    @Column(name = "housing_allowance", precision = 14, scale = 2)
    private BigDecimal housingAllowance;

    @Column(name = "cola_amount", precision = 14, scale = 2)
    private BigDecimal colaAmount;

    @Column(name = "hardship_amount", precision = 14, scale = 2)
    private BigDecimal hardshipAmount;

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

    public String getAssignmentNo() { return assignmentNo; }
    public void setAssignmentNo(String assignmentNo) { this.assignmentNo = assignmentNo; }

    public UUID getEmployeeId() { return employeeId; }
    public void setEmployeeId(UUID employeeId) { this.employeeId = employeeId; }

    public String getHostCountry() { return hostCountry; }
    public void setHostCountry(String hostCountry) { this.hostCountry = hostCountry; }

    public String getHostCity() { return hostCity; }
    public void setHostCity(String hostCity) { this.hostCity = hostCity; }

    public String getHostEntity() { return hostEntity; }
    public void setHostEntity(String hostEntity) { this.hostEntity = hostEntity; }

    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getVisaType() { return visaType; }
    public void setVisaType(String visaType) { this.visaType = visaType; }

    public LocalDate getVisaExpiry() { return visaExpiry; }
    public void setVisaExpiry(LocalDate visaExpiry) { this.visaExpiry = visaExpiry; }

    public BigDecimal getHousingAllowance() { return housingAllowance; }
    public void setHousingAllowance(BigDecimal housingAllowance) { this.housingAllowance = housingAllowance; }

    public BigDecimal getColaAmount() { return colaAmount; }
    public void setColaAmount(BigDecimal colaAmount) { this.colaAmount = colaAmount; }

    public BigDecimal getHardshipAmount() { return hardshipAmount; }
    public void setHardshipAmount(BigDecimal hardshipAmount) { this.hardshipAmount = hardshipAmount; }

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
