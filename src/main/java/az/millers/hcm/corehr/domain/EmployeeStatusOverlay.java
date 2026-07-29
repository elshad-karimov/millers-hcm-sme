package az.millers.hcm.corehr.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.common.history.EffectiveDatedRecord;
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
 * Concurrent status overlay on top of {@code Employee.employmentStatus}
 * (M78 / P2-13). Implements the M62 {@link EffectiveDatedRecord} pattern so
 * the shared {@code closeOn()} default method handles end-dating.
 *
 * <p>The {@link #source} + {@link #sourceId} pair is informational — when
 * an overlay is created from a leave request, the source row's ID is stored
 * so the UI can deep-link back.
 */
@Entity
@Table(name = "employee_status_overlay", schema = "core_hr")
@Getter
@Setter
@NoArgsConstructor
public class EmployeeStatusOverlay implements EffectiveDatedRecord {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EmploymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OverlaySource source = OverlaySource.MANUAL;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(columnDefinition = "text")
    private String notes;

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
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public enum OverlaySource {
        MANUAL,
        LEAVE_REQUEST,
        BUSINESS_TRIP,
        PERMISSION,
        DISCIPLINARY,
        LIFECYCLE
    }
}
