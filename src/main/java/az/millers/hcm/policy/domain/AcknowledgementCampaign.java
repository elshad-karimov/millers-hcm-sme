package az.millers.hcm.policy.domain;

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

/**
 * M490 — Policy re-acknowledgement campaign. HR launches a campaign targeting
 * employees to re-acknowledge a specific policy version. Progress is tracked
 * against the target audience (all employees or a specific department).
 */
@Entity
@Table(name = "acknowledgement_campaign", schema = "policy")
@Getter
@Setter
@NoArgsConstructor
public class AcknowledgementCampaign {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId = "default";

    @Column(name = "policy_id", nullable = false)
    private UUID policyId;

    @Column(name = "policy_version", nullable = false)
    private int policyVersion;

    @Column(nullable = false, length = 240)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CampaignAudience audience = CampaignAudience.ALL;

    /** FK to org_unit when audience = DEPARTMENT. */
    @Column(name = "audience_ref")
    private UUID audienceRef;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CampaignStatus status = CampaignStatus.DRAFT;

    @Column(name = "launched_at")
    private OffsetDateTime launchedAt;

    @Column(name = "launched_by", length = 80)
    private String launchedBy;

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
        if (dueDate == null) {
            dueDate = LocalDate.now().plusDays(14);
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
