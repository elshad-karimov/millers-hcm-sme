package az.millers.hcm.config.domain;

import java.io.Serializable;
import java.time.OffsetDateTime;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "tenant_setting", schema = "config")
@IdClass(TenantSetting.TenantSettingId.class)
@Data
public class TenantSetting {
    // tenant_id is part of the composite primary key (per-tenant settings keyed
    // by (tenant_id, key)), so isolation comes from the key itself — Hibernate's
    // @TenantId discriminator is illegal on an @Id field and unnecessary here.
    @Id
    @Column(name = "tenant_id", nullable = false)
    private String tenantId = "default";

    @Id
    @Column(name = "key", nullable = false, length = 100)
    private String key;

    @Column(name = "value", columnDefinition = "text")
    private String value;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Column(name = "updated_by")
    private String updatedBy;

    @Data
    public static class TenantSettingId implements Serializable {
        private String tenantId;
        private String key;
    }
}
