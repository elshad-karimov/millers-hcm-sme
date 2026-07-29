package az.millers.hcm.compensation.domain;

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
 * M362 — Compensation exceptions registry.
 * Tracks out-of-band salary changes, budget overruns, threshold violations.
 */
@Entity
@Table(name = "compensation_exception", schema = "compensation")
@Getter
@Setter
@NoArgsConstructor
public class CompensationException {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 40)
    private CompensationExceptionSource sourceType;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "exception_type", nullable = false, length = 30)
    private CompensationExceptionType exceptionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CompensationExceptionSeverity severity = CompensationExceptionSeverity.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CompensationExceptionStatus status = CompensationExceptionStatus.OPEN;

    @Column(length = 1000)
    private String reason;

    @Column(name = "raised_at", nullable = false, updatable = false)
    private OffsetDateTime raisedAt;

    @Column(name = "resolved_by", length = 200)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        raisedAt = OffsetDateTime.now();
    }
}
