package az.millers.hcm.performance.domain;

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

/**
 * HCM_15 M409 — HR-only talent profile (PRD 15 §3/§9/§14). HIGHLY SENSITIVE:
 * never exposed to the employee themselves (gap-check visibility default).
 */
@Entity
@Table(name = "talent_profile", schema = "performance")
@Getter
@Setter
@NoArgsConstructor
public class TalentProfile {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "hipo_flag", nullable = false)
    private boolean hipoFlag = false;

    /** NINE_BOX | MANUAL */
    @Column(name = "hipo_source", length = 20)
    private String hipoSource;

    /** LOW | MEDIUM | HIGH | CRITICAL — manual HR judgment. */
    @Column(name = "retention_risk", length = 10)
    private String retentionRisk;

    @Column(name = "risk_reason", length = 1000)
    private String riskReason;

    @Column(name = "retention_action_plan", length = 2000)
    private String retentionActionPlan;

    /** NONE | INTERNAL | RELOCATION | INTERNATIONAL */
    @Column(name = "mobility_willingness", length = 20)
    private String mobilityWillingness;

    @Column(name = "relocation_preference", length = 300)
    private String relocationPreference;

    @Column(name = "career_aspirations", length = 2000)
    private String careerAspirations;

    @Column(length = 2000)
    private String notes;

    @Column(name = "updated_by", length = 80)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void onUpdate() { updatedAt = OffsetDateTime.now(); }
}
