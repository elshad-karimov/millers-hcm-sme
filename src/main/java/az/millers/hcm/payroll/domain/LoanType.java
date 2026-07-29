package az.millers.hcm.payroll.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.TenantId;

/**
 * M460 — Tenant-specific loan type catalog with eligibility rules.
 */
@Entity
@Table(name = "loan_type", schema = "payroll")
public class LoanType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 80, updatable = false)
    private String tenantId;

    @Column(nullable = false, length = 60)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "max_amount", precision = 12, scale = 2)
    private BigDecimal maxAmount;

    @Column(name = "max_multiple_of_net", nullable = false, precision = 4, scale = 1)
    private BigDecimal maxMultipleOfNet = new BigDecimal("3.0");

    @Column(name = "max_months", nullable = false)
    private Integer maxMonths = 24;

    @Column(name = "interest_rate_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal interestRatePct = BigDecimal.ZERO;

    @Column(name = "min_tenure_months", nullable = false)
    private Integer minTenureMonths = 6;

    @Column(name = "max_active_loans", nullable = false)
    private Integer maxActiveLoans = 1;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "created_by", length = 80)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Column(name = "updated_by", length = 80)
    private String updatedBy;

    // Getters and setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getMaxAmount() {
        return maxAmount;
    }

    public void setMaxAmount(BigDecimal maxAmount) {
        this.maxAmount = maxAmount;
    }

    public BigDecimal getMaxMultipleOfNet() {
        return maxMultipleOfNet;
    }

    public void setMaxMultipleOfNet(BigDecimal maxMultipleOfNet) {
        this.maxMultipleOfNet = maxMultipleOfNet;
    }

    public Integer getMaxMonths() {
        return maxMonths;
    }

    public void setMaxMonths(Integer maxMonths) {
        this.maxMonths = maxMonths;
    }

    public BigDecimal getInterestRatePct() {
        return interestRatePct;
    }

    public void setInterestRatePct(BigDecimal interestRatePct) {
        this.interestRatePct = interestRatePct;
    }

    public Integer getMinTenureMonths() {
        return minTenureMonths;
    }

    public void setMinTenureMonths(Integer minTenureMonths) {
        this.minTenureMonths = minTenureMonths;
    }

    public Integer getMaxActiveLoans() {
        return maxActiveLoans;
    }

    public void setMaxActiveLoans(Integer maxActiveLoans) {
        this.maxActiveLoans = maxActiveLoans;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
