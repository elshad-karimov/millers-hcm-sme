package az.millers.hcm.leave.domain;

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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "unauthorized_absence_conversion", schema = "leave_mgmt",
        uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "absence_date"}))
@Getter
@Setter
@NoArgsConstructor
public class UnauthorizedAbsenceConversion {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId = "default";

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "absence_date", nullable = false)
    private LocalDate absenceDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AbsenceConversionStatus status = AbsenceConversionStatus.PENDING;

    @Column(name = "leave_type_id")
    private UUID leaveTypeId;

    @Column(name = "leave_request_id")
    private UUID leaveRequestId;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "resolved_by", length = 160)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", length = 160)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
