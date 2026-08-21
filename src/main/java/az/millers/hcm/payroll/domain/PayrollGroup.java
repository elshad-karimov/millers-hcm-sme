package az.millers.hcm.payroll.domain;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/**
 * Group of employees sharing a pay calendar + bank file format + currency
 * (M75 / P2-19). Same override-or-fallback pattern as M66 LeaveGroup —
 * the seeded DEFAULT group catches every employee whose payroll_group_id
 * is NULL.
 *
 * <p>{@link #rulesJson} is reserved for future jurisdiction-override rules
 * (e.g. different DSMF rate for an offshore subsidiary). The PayrollEngine
 * doesn't consume it yet; M75 ships the storage layer.
 */
@Entity
@Table(name = "payroll_group", schema = "payroll")
@Getter
@Setter
@NoArgsConstructor
public class PayrollGroup {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "pay_cycle", nullable = false, length = 20)
    private PayCycle payCycle = PayCycle.MONTHLY;

    @Enumerated(EnumType.STRING)
    @Column(name = "bank_file_format", nullable = false, length = 20)
    private BankFileFormat bankFileFormat = BankFileFormat.CSV;

    @Column(name = "default_currency", nullable = false, length = 3)
    private String defaultCurrency = "AZN";

    /** Future rule overrides (e.g. jurisdiction-specific DSMF rates). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rules_json", columnDefinition = "jsonb")
    private JsonNode rulesJson;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "is_default", nullable = false)
    private boolean defaultGroup = false;

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
