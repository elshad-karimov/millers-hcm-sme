package az.millers.hcm.selfservice.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import az.millers.hcm.security.SecurityRoles;
import az.millers.hcm.selfservice.domain.KnowledgeArticle;
import az.millers.hcm.selfservice.service.KnowledgeArticleService;

/**
 * M436 — Knowledge base endpoints.
 * Published articles readable by any authenticated user; mutations HR only.
 */
@RestController
@RequestMapping("/api/helpdesk/knowledge")
public class KnowledgeArticleController {

    private final KnowledgeArticleService service;

    public KnowledgeArticleController(KnowledgeArticleService service) {
        this.service = service;
    }

    @GetMapping("/published")
    @PreAuthorize("isAuthenticated()")
    public List<KnowledgeArticle> listPublished() {
        return service.listPublished();
    }

    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public List<KnowledgeArticle> search(@RequestParam String keyword) {
        return service.search(keyword);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public KnowledgeArticle get(@PathVariable UUID id) {
        KnowledgeArticle article = service.get(id);
        service.incrementViews(id);
        return article;
    }

    @PostMapping("/{id}/vote")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> vote(@PathVariable UUID id, @RequestParam boolean helpful) {
        service.voteHelpful(id, helpful);
        return ResponseEntity.ok().build();
    }

    // ── HR admin mutations ──

    @GetMapping
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public List<KnowledgeArticle> listAll() {
        return service.listAll();
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public KnowledgeArticle create(@RequestBody CreateArticleRequest request) {
        return service.create(request.code, request.title, request.summary,
                request.category, request.tags, request.body);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public KnowledgeArticle update(@PathVariable UUID id, @RequestBody CreateArticleRequest request) {
        return service.update(id, request.code, request.title, request.summary,
                request.category, request.tags, request.body);
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public KnowledgeArticle publish(@PathVariable UUID id) {
        return service.publish(id);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public KnowledgeArticle archive(@PathVariable UUID id) {
        return service.archive(id);
    }

    record CreateArticleRequest(String code, String title, String summary,
                                 String category, String tags, String body) {}
}
