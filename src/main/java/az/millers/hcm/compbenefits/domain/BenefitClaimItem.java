package az.millers.hcm.compbenefits.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
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

/** HCM_11 M381 — a line item of a {@link BenefitClaim}. */
@Entity
@Table(name = "benefit_claim_item", schema = "compbenefits")
@Getter
@Setter
@NoArgsConstructor
public class BenefitClaimItem {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 64, updatable = false)
    private String tenantId;

    @Column(name = "claim_id", nullable = false)
    private UUID claimId;

    @Column(name = "service_date")
    private LocalDate serviceDate;

    @Column(nullable = false, length = 300)
    private String description;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
