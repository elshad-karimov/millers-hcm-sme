package az.millers.hcm.selfservice.service;
import az.millers.hcm.common.tenant.TenantContext;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.selfservice.domain.KnowledgeArticle;
import az.millers.hcm.selfservice.domain.KnowledgeArticleStatus;
import az.millers.hcm.selfservice.repo.KnowledgeArticleRepository;
import az.millers.hcm.common.BusinessNumbers;

/**
 * M436 — Knowledge base service.
 * HR creates/edits/publishes articles; employees search and view published.
 */
@Service
public class KnowledgeArticleService {

    private static final String MODULE = "self-service";
    private static final String ENTITY = "KnowledgeArticle";

    private final KnowledgeArticleRepository repo;
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public KnowledgeArticleService(KnowledgeArticleRepository repo,
                                    NamedParameterJdbcTemplate jdbc,
                                    AuditService audit,
                                    CurrentRequest currentRequest) {
        this.repo = repo;
        this.jdbc = jdbc;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional
    public KnowledgeArticle create(String code, String title, String summary, String category,
                                     String tags, String body) {
        KnowledgeArticle article = new KnowledgeArticle();
        article.setTenantId(TenantContext.current());
        article.setCode(code);
        article.setTitle(title);
        article.setSummary(summary);
        article.setCategory(category);
        article.setTags(tags);
        article.setBody(body);
        article.setStatus(KnowledgeArticleStatus.DRAFT);
        article.setCreatedBy(currentRequest.username());
        article.setUpdatedBy(currentRequest.username());

        KnowledgeArticle saved = repo.save(article);

        // Generate article number
        String articleNo = BusinessNumbers.format("KB", 5, jdbc.queryForObject(
                "SELECT nextval('selfservice.knowledge_article_no_seq')",
                Map.of(), Long.class));
        saved.setArticleNo(articleNo);
        saved = repo.save(saved);

        audit.record(MODULE, ENTITY, saved.getId().toString(), "CREATE", null, null);
        return saved;
    }

    @Transactional
    public KnowledgeArticle update(UUID id, String code, String title, String summary,
                                     String category, String tags, String body) {
        KnowledgeArticle article = get(id);
        article.setCode(code);
        article.setTitle(title);
        article.setSummary(summary);
        article.setCategory(category);
        article.setTags(tags);
        article.setBody(body);
        article.setUpdatedBy(currentRequest.username());
        article.setUpdatedAt(OffsetDateTime.now());
        KnowledgeArticle saved = repo.save(article);
        audit.record(MODULE, ENTITY, id.toString(), "UPDATE", null, null);
        return saved;
    }

    @Transactional
    public KnowledgeArticle publish(UUID id) {
        KnowledgeArticle article = get(id);
        article.setStatus(KnowledgeArticleStatus.PUBLISHED);
        article.setUpdatedBy(currentRequest.username());
        article.setUpdatedAt(OffsetDateTime.now());
        KnowledgeArticle saved = repo.save(article);
        audit.record(MODULE, ENTITY, id.toString(), "PUBLISH", null, null);
        return saved;
    }

    @Transactional
    public KnowledgeArticle archive(UUID id) {
        KnowledgeArticle article = get(id);
        article.setStatus(KnowledgeArticleStatus.ARCHIVED);
        article.setUpdatedBy(currentRequest.username());
        article.setUpdatedAt(OffsetDateTime.now());
        KnowledgeArticle saved = repo.save(article);
        audit.record(MODULE, ENTITY, id.toString(), "ARCHIVE", null, null);
        return saved;
    }

    @Transactional
    public void incrementViews(UUID id) {
        KnowledgeArticle article = get(id);
        article.setViewCount(article.getViewCount() + 1);
        repo.save(article);
    }

    @Transactional
    public void voteHelpful(UUID id, boolean helpful) {
        KnowledgeArticle article = get(id);
        if (helpful) {
            article.setHelpfulVotes(article.getHelpfulVotes() + 1);
        } else {
            article.setNotHelpfulVotes(article.getNotHelpfulVotes() + 1);
        }
        repo.save(article);
        // Note: no dedup table per M436 design — simple counters
    }

    @Transactional(readOnly = true)
    public List<KnowledgeArticle> listPublished() {
        return repo.findByTenantIdAndStatusOrderByCreatedAtDesc(TenantContext.current(), KnowledgeArticleStatus.PUBLISHED)
                .stream()
                .filter(a -> TenantContext.current().equals(a.getTenantId()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<KnowledgeArticle> listAll() {
        // HR sees all statuses
        return repo.findByTenantIdOrderByCreatedAtDesc(TenantContext.current())
                .stream()
                .filter(a -> TenantContext.current().equals(a.getTenantId()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<KnowledgeArticle> search(String keyword) {
        // Full-text search on published articles
        String sql = """
            SELECT * FROM selfservice.knowledge_article
            WHERE tenant_id = :tenantId
              AND status = 'PUBLISHED'
              AND (
                LOWER(title) LIKE :keyword
                OR LOWER(summary) LIKE :keyword
                OR LOWER(body) LIKE :keyword
              )
            ORDER BY created_at DESC
        """;

        return jdbc.query(sql,
                Map.of("tenantId", TenantContext.current(), "keyword", "%" + keyword.toLowerCase() + "%"),
                (rs, rowNum) -> {
                    KnowledgeArticle article = new KnowledgeArticle();
                    article.setId(UUID.fromString(rs.getString("id")));
                    article.setTenantId(rs.getString("tenant_id"));
                    article.setArticleNo(rs.getString("article_no"));
                    article.setCode(rs.getString("code"));
                    article.setTitle(rs.getString("title"));
                    article.setSummary(rs.getString("summary"));
                    article.setCategory(rs.getString("category"));
                    article.setTags(rs.getString("tags"));
                    article.setBody(rs.getString("body"));
                    article.setStatus(KnowledgeArticleStatus.valueOf(rs.getString("status")));
                    article.setVersion(rs.getInt("version"));
                    article.setViewCount(rs.getInt("view_count"));
                    article.setHelpfulVotes(rs.getInt("helpful_votes"));
                    article.setNotHelpfulVotes(rs.getInt("not_helpful_votes"));
                    article.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));
                    article.setCreatedBy(rs.getString("created_by"));
                    article.setUpdatedAt(rs.getObject("updated_at", OffsetDateTime.class));
                    article.setUpdatedBy(rs.getString("updated_by"));
                    return article;
                })
                .stream()
                .filter(a -> TenantContext.current().equals(a.getTenantId()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public KnowledgeArticle get(UUID id) {
        KnowledgeArticle article = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Knowledge article not found: " + id));
        if (!TenantContext.current().equals(article.getTenantId())) {
            throw new ResourceNotFoundException("Knowledge article not found: " + id);
        }

        // Non-PUBLISHED articles visible only to HR roles
        if (article.getStatus() != KnowledgeArticleStatus.PUBLISHED) {
            boolean isHR = currentRequest.hasRole("HR_ADMIN")
                        || currentRequest.hasRole("HR_SPECIALIST")
                        || currentRequest.hasRole("SYSTEM_ADMIN");
            if (!isHR) {
                throw new ResourceNotFoundException("Knowledge article not found: " + id);
            }
        }

        return article;
    }
}
