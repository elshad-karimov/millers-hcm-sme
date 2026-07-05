package az.millers.hcm.ehs.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "injury_report", schema = "ehs")
@Data
public class InjuryReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId = "default";

    @Column(name = "incident_id")
    private UUID incidentId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "injury_type", length = 60)
    private String injuryType;

    @Column(name = "body_part", length = 60)
    private String bodyPart;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 20)
    private IncidentSeverity severity;

    @Column(name = "medical_treatment", nullable = false)
    private Boolean medicalTreatment = false;

    @Column(name = "first_aid", nullable = false)
    private Boolean firstAid = false;

    @Column(name = "hospital", nullable = false)
    private Boolean hospital = false;

    @Column(name = "lost_time_days", nullable = false)
    private Integer lostTimeDays = 0;

    @Column(name = "restricted_duty", nullable = false)
    private Boolean restrictedDuty = false;

    @Column(name = "insurance_claim_ref", length = 100)
    private String insuranceClaimRef;

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
