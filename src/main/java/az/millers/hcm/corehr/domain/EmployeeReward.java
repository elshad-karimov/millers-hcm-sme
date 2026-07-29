package az.millers.hcm.corehr.domain;

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
import org.hibernate.annotations.TenantId;

/**
 * Recognition / award record for an employee (M72 / P2-18).
 *
 * <p>Standalone from the payroll bonus engine — those rows ({@code
 * payroll.payroll_bonus}) are accounted payments that flow through a payroll
 * run. Rewards captured here are the recognition events themselves: spot
 * awards, certificates, appreciation letters, fast-track promotion
 * recommendations. {@code BONUS_RECOMMENDATION} is the bridge — HR records a
 * recommendation here, payroll picks it up via the existing bonus matrix flow.
 */
@Entity
@Table(name = "employee_reward", schema = "core_hr")
@Getter
@Setter
@NoArgsConstructor
public class EmployeeReward {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reward_type", nullable = false, length = 30)
    private RewardType rewardType;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "award_value", precision = 12, scale = 2)
    private BigDecimal awardValue;

    @Column(length = 3)
    private String currency;

    @Column(name = "awarded_by", length = 80)
    private String awardedBy;

    @Column(name = "awarded_at", nullable = false)
    private LocalDate awardedAt;

    @Column(name = "certificate_url", length = 500)
    private String certificateUrl;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", length = 80)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by", length = 80)
    private String updatedBy;

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
