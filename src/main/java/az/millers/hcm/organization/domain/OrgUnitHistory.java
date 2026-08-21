package az.millers.hcm.organization.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
 * Append-only audit slice per org-unit change (M81). Sits next to the
 * generic {@code audit.audit_log} so the Org page can render a per-unit
 * lifecycle without scanning the monthly-partitioned global audit table.
 */
@Entity
@Table(name = "org_unit_history", schema = "organization")
@Getter
@Setter
@NoArgsConstructor
public class OrgUnitHistory {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "org_unit_id", nullable = false)
    private UUID orgUnitId;

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_kind", nullable = false, length = 20)
    private ChangeKind changeKind;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_value", columnDefinition = "jsonb")
    private JsonNode beforeValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_value", columnDefinition = "jsonb")
    private JsonNode afterValue;

    @Column(name = "change_reason", columnDefinition = "text")
    private String changeReason;

    @Column(name = "changed_at", nullable = false)
    private OffsetDateTime changedAt;

    @Column(name = "changed_by", length = 80)
    private String changedBy;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (changedAt == null) changedAt = OffsetDateTime.now();
    }

    public enum ChangeKind {
        CREATE,
        UPDATE,
        REMOVE,
        MOVE,
        RENAME,
        HEAD_CHANGE,
        POLICY_CHANGE,
        /** M144 — lifecycle state transition (§26). */
        LIFECYCLE_CHANGE
    }
}
