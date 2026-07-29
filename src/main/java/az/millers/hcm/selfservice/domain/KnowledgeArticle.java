package az.millers.hcm.selfservice.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.TenantId;

/**
 * M436 — HR knowledge base article (FAQs, policy summaries, how-tos).
 * DRAFT visible to HR only; PUBLISHED readable by all authenticated employees.
 */
@Data
@Entity
@Table(name = "knowledge_article", schema = "selfservice")
public class KnowledgeArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "article_no")
    private String articleNo;

    @Column(nullable = false, length = 100)
    private String code;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 2000)
    private String summary;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(length = 500)
    private String tags;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private KnowledgeArticleStatus status = KnowledgeArticleStatus.DRAFT;

    @Column(nullable = false)
    private Integer version = 1;

    @Column(name = "view_count", nullable = false)
    private Integer viewCount = 0;

    @Column(name = "helpful_votes", nullable = false)
    private Integer helpfulVotes = 0;

    @Column(name = "not_helpful_votes", nullable = false)
    private Integer notHelpfulVotes = 0;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
