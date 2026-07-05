package az.millers.hcm.ehs.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "risk_assessment", schema = "ehs")
@Data
public class RiskAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId = "default";

    @Column(name = "work_location_id")
    private UUID workLocationId;

    @Column(name = "org_unit_id")
    private UUID orgUnitId;

    @Column(name = "job_task", nullable = false, length = 200)
    private String jobTask;

    @Column(name = "hazard", nullable = false, length = 2000)
    private String hazard;

    @Column(name = "likelihood", nullable = false)
    private Integer likelihood; // 1-5

    @Column(name = "impact", nullable = false)
    private Integer impact; // 1-5

    @Column(name = "risk_score", nullable = false)
    private Integer riskScore; // likelihood × impact

    @Column(name = "control_measures", length = 2000)
    private String controlMeasures;

    @Column(name = "responsible_username", length = 255)
    private String responsibleUsername;

    @Column(name = "review_date")
    private LocalDate reviewDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RiskAssessmentStatus status = RiskAssessmentStatus.DRAFT;

    @Column(name = "approved_by", length = 255)
    private String approvedBy;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (updatedAt == null) updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
