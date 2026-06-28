package az.millers.hcm.attendance.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * M332: Exception configuration per tenant.
 *
 * <p>Defines which exceptions are enabled, their thresholds, severity, and
 * whether to auto-notify when triggered.
 */
@Entity
@Table(name = "exception_config", schema = "attendance")
@Getter
@Setter
@NoArgsConstructor
public class ExceptionConfig {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "exception_type", length = 50, nullable = false)
    private String exceptionType;

    @Column(name = "threshold_minutes", nullable = false)
    private int thresholdMinutes = 0;

    @Column(length = 20, nullable = false)
    private String severity = "WARNING";

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "auto_notify", nullable = false)
    private boolean autoNotify = false;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
