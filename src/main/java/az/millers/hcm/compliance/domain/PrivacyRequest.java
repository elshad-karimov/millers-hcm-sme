package az.millers.hcm.compliance.domain;

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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/**
 * M472 — Privacy request (GDPR compliance: access, export, delete, correction).
 */
@Entity
@Table(name = "privacy_request", schema = "compliance")
@Getter
@Setter
@NoArgsConstructor
public class PrivacyRequest {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 64, updatable = false)
    private String tenantId;

    @Column(name = "employee_id")
    private UUID employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 20)
    private RequestType requestType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestStatus status = RequestStatus.OPEN;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", length = 200)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by", length = 200)
    private String updatedBy;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (dueDate == null) dueDate = LocalDate.now().plusDays(30);
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    public enum RequestType {
        ACCESS,
        EXPORT,
        DELETE,
        CORRECTION
    }

    public enum RequestStatus {
        OPEN,
        IN_PROGRESS,
        COMPLETED,
        REJECTED
    }
}
