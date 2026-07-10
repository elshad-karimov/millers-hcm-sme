package az.millers.hcm.integration.domain;

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

/**
 * M492 — Integration execution log. Purged after 90 days.
 */
@Entity
@Table(name = "integration_log", schema = "integration")
@Getter
@Setter
@NoArgsConstructor
public class IntegrationLog {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId = "default";

    @Column(name = "config_id", nullable = false)
    private UUID configId;

    @Column(name = "run_at", nullable = false)
    private OffsetDateTime runAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IntegrationStatus status;

    @Column(name = "records_processed")
    private Integer recordsProcessed = 0;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (runAt == null) runAt = OffsetDateTime.now();
    }
}
