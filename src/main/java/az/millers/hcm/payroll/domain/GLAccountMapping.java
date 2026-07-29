package az.millers.hcm.payroll.domain;

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
 * M355 — Maps payroll component kind/code to GL account for journal generation.
 */
@Entity
@Table(name = "gl_account_mapping", schema = "payroll")
@Getter
@Setter
@NoArgsConstructor
public class GLAccountMapping {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "component_kind", length = 20)
    private String componentKind;

    @Column(name = "component_code", length = 50)
    private String componentCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private GLAccountType accountType;

    @Column(name = "gl_account_code", nullable = false, length = 50)
    private String glAccountCode;

    @Column(name = "gl_account_name", nullable = false, length = 200)
    private String glAccountName;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = OffsetDateTime.now();
    }
}
