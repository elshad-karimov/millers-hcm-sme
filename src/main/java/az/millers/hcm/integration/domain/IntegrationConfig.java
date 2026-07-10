package az.millers.hcm.integration.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

/**
 * M492 — Integration config registry. External integrations (webhooks, file
 * feeds, API connectors). endpoint_url encrypted; credentials stored by
 * reference only.
 */
@Entity
@Table(name = "integration_config", schema = "integration")
@Getter
@Setter
@NoArgsConstructor
public class IntegrationConfig {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId = "default";

    @Column(nullable = false, length = 80)
    private String code;

    @Column(nullable = false, length = 240)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private IntegrationDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IntegrationType type;

    /** Encrypted via EncryptedStringConverter (AES-256). */
    @Convert(converter = az.millers.hcm.security.crypto.EncryptedStringConverter.class)
    @Column(name = "endpoint_url", length = 500)
    private String endpointUrl;

    /** Reference name only (e.g., "ERP-API-KEY") — never store secrets here. */
    @Column(name = "credentials_ref", length = 200)
    private String credentialsRef;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "last_run_at")
    private OffsetDateTime lastRunAt;

    @Column(name = "last_status", length = 20)
    private String lastStatus;

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
}
