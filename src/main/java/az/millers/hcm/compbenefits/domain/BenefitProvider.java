package az.millers.hcm.compbenefits.domain;

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
 * HCM_11 M374 — Benefit provider / vendor master. Insurers, pension funds, clinics
 * and other vendors that supply benefit plans, with contract tracking.
 */
@Entity
@Table(name = "benefit_provider", schema = "compbenefits")
@Getter
@Setter
@NoArgsConstructor
public class BenefitProvider {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 64, updatable = false)
    private String tenantId;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 30)
    private BenefitProviderType providerType = BenefitProviderType.OTHER;

    @Column(name = "contact_name", length = 160)
    private String contactName;

    @Column(name = "contact_email", length = 200)
    private String contactEmail;

    @Column(name = "contact_phone", length = 60)
    private String contactPhone;

    @Column(length = 200)
    private String website;

    @Column(name = "contract_no", length = 80)
    private String contractNo;

    @Column(name = "contract_start")
    private LocalDate contractStart;

    @Column(name = "contract_end")
    private LocalDate contractEnd;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_by", length = 80)
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
    void onUpdate() { updatedAt = OffsetDateTime.now(); }
}
