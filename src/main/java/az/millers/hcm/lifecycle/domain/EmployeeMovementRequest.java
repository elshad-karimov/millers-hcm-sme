package az.millers.hcm.lifecycle.domain;

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
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "employee_movement_request", schema = "lifecycle")
@Data
public class EmployeeMovementRequest {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId = "default";

    @Column(name = "request_no", nullable = false, unique = true, length = 20)
    private String requestNo;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 20)
    private MovementType movementType;

    @Column(name = "proposed_position_id")
    private UUID proposedPositionId;

    @Column(name = "proposed_org_unit_id")
    private UUID proposedOrgUnitId;

    @Column(name = "proposed_grade", length = 50)
    private String proposedGrade;

    @Column(name = "proposed_salary", precision = 12, scale = 2)
    private BigDecimal proposedSalary;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "justification", length = 2000)
    private String justification;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MovementRequestStatus status = MovementRequestStatus.DRAFT;

    @Column(name = "workflow_instance_id")
    private UUID workflowInstanceId;

    @Column(name = "requested_by", length = 80)
    private String requestedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", length = 80)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by", length = 80)
    private String updatedBy;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }
}
