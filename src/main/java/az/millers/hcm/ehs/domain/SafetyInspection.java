package az.millers.hcm.ehs.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "safety_inspection", schema = "ehs")
@Data
public class SafetyInspection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId = "default";

    @Column(name = "work_location_id")
    private UUID workLocationId;

    @Column(name = "inspection_date", nullable = false)
    private LocalDate inspectionDate;

    @Column(name = "inspector_username", nullable = false, length = 255)
    private String inspectorUsername;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "overall_score")
    private Integer overallScore; // % OK

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InspectionStatus status = InspectionStatus.SCHEDULED;

    @Column(name = "notes", length = 2000)
    private String notes;

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
