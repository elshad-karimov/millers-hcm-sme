package az.millers.hcm.corehr.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.TenantId;

/**
 * M439 — Document category for employee documents.
 * Defines categories (CONTRACT, ID_CARD, PASSPORT, etc.) with retention + renewal rules.
 */
@Data
@Entity
@Table(name = "document_category", schema = "core_hr")
public class DocumentCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Boolean mandatory = false;

    @Column(name = "retention_days")
    private Integer retentionDays;

    @Column(name = "auto_create_renewal_request", nullable = false)
    private Boolean autoCreateRenewalRequest = false;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
