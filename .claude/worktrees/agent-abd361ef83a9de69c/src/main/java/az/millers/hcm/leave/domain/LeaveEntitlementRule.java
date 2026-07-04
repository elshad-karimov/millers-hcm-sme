package az.millers.hcm.leave.domain;

import java.math.BigDecimal;
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

import az.millers.hcm.corehr.domain.EmploymentType;

@Entity
@Table(schema = "leave_mgmt", name = "leave_entitlement_rule")
@Getter
@Setter
@NoArgsConstructor
public class LeaveEntitlementRule {

    @Id
    private UUID id;

    @Column(name = "leave_type_id", nullable = false)
    private UUID leaveTypeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", length = 20)
    private EmploymentType employmentType;

    @Column(name = "min_tenure_months")
    private Integer minTenureMonths;

    @Column(name = "max_tenure_months")
    private Integer maxTenureMonths;

    @Column(name = "annual_entitlement_days", nullable = false, precision = 5, scale = 2)
    private BigDecimal annualEntitlementDays;

    @Column(name = "priority", nullable = false)
    private int priority;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    /** Returns true if the rule matches the given employment type and completed months of tenure. */
    public boolean matches(EmploymentType empType, int tenureMonths) {
        if (employmentType != null && employmentType != empType) return false;
        if (minTenureMonths != null && tenureMonths < minTenureMonths) return false;
        if (maxTenureMonths != null && tenureMonths > maxTenureMonths) return false;
        return true;
    }
}
