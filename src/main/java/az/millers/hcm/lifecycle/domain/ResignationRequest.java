package az.millers.hcm.lifecycle.domain;

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

@Entity
@Table(name = "resignation_request", schema = "lifecycle")
@Getter
@Setter
@NoArgsConstructor
public class ResignationRequest {

    @Id
    private UUID id;

    @Column(name = "resignation_no", nullable = false, unique = true)
    private String resignationNo;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "resignation_date", nullable = false)
    private LocalDate resignationDate;

    @Column(name = "proposed_last_working_date", nullable = false)
    private LocalDate proposedLastWorkingDate;

    @Column(name = "calculated_last_working_date")
    private LocalDate calculatedLastWorkingDate;

    @Column(name = "approved_last_working_date")
    private LocalDate approvedLastWorkingDate;

    @Column(name = "notice_days_calculated")
    private Integer noticeDaysCalculated;

    @Column(name = "reason_text", columnDefinition = "text")
    private String reasonText;

    @Column(columnDefinition = "text")
    private String comments;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ResignationStatus status;

    @Column(name = "workflow_instance_id")
    private UUID workflowInstanceId;

    @Column(name = "withdrawal_reason", columnDefinition = "text")
    private String withdrawalReason;

    @Column(name = "withdrawn_at")
    private OffsetDateTime withdrawnAt;

    @Column(name = "created_by", length = 100)
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
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
