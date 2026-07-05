package az.millers.hcm.selfservice.domain;

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

/**
 * M437 — Comment/note on an HR service request.
 * Internal notes visible to HR only; employees see only non-internal comments on their own requests.
 */
@Data
@Entity
@Table(name = "hr_service_request_comment", schema = "selfservice")
public class HrServiceRequestComment {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId = "default";

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "author_username", nullable = false)
    private String authorUsername;

    @Column(name = "is_internal", nullable = false)
    private Boolean isInternal = false;

    @Column(nullable = false, length = 4000)
    private String body;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

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
