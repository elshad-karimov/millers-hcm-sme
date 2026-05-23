package az.millers.hcm.leave.domain;

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

@Entity
@Table(name = "leave_request", schema = "leave_mgmt")
@Getter
@Setter
@NoArgsConstructor
public class LeaveRequest {

    @Id
    private UUID id;

    @Column(name = "request_no", nullable = false, unique = true)
    private String requestNo;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "leave_type_id", nullable = false)
    private UUID leaveTypeId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "half_day", nullable = false)
    private boolean halfDay;

    @Column(name = "total_days", nullable = false, precision = 7, scale = 2)
    private BigDecimal totalDays;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(name = "attachment_url")
    private String attachmentUrl;

    @Column(name = "replacement_employee_id")
    private UUID replacementEmployeeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeaveRequestStatus status;

    @Column(name = "workflow_instance_id")
    private UUID workflowInstanceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by")
    private String createdBy;

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
