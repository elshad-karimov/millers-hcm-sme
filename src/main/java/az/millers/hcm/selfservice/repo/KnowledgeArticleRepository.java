package az.millers.hcm.selfservice.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.selfservice.domain.KnowledgeArticle;
import az.millers.hcm.selfservice.domain.KnowledgeArticleStatus;

/**
 * M436 — Knowledge article repository.
 */
public interface KnowledgeArticleRepository extends JpaRepository<KnowledgeArticle, UUID> {

    List<KnowledgeArticle> findByTenantIdAndStatusOrderByCreatedAtDesc(String tenantId, KnowledgeArticleStatus status);

    List<KnowledgeArticle> findByTenantIdOrderByCreatedAtDesc(String tenantId);
}
