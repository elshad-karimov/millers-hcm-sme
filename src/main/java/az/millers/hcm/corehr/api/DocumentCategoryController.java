package az.millers.hcm.corehr.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import az.millers.hcm.corehr.domain.DocumentCategory;
import az.millers.hcm.corehr.service.DocumentCategoryService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M439 — Document category endpoints.
 */
@RestController
@RequestMapping("/api/documents/categories")
public class DocumentCategoryController {

    private final DocumentCategoryService service;

    public DocumentCategoryController(DocumentCategoryService service) {
        this.service = service;
    }

    public record CreateCategoryRequest(
            String code,
            String name,
            Boolean mandatory,
            Integer retentionDays,
            Boolean autoRenewal,
            Integer sortOrder) {}

    public record UpdateCategoryRequest(
            String name,
            Boolean mandatory,
            Integer retentionDays,
            Boolean autoRenewal,
            Integer sortOrder,
            Boolean active) {}

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<DocumentCategory> listAll() {
        return service.listAll();
    }

    @GetMapping("/active")
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<DocumentCategory> listActive() {
        return service.listActive();
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public DocumentCategory create(@RequestBody CreateCategoryRequest req) {
        return service.create(req.code(), req.name(), req.mandatory(),
                req.retentionDays(), req.autoRenewal(), req.sortOrder());
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public DocumentCategory update(@PathVariable UUID id, @RequestBody UpdateCategoryRequest req) {
        return service.update(id, req.name(), req.mandatory(), req.retentionDays(),
                req.autoRenewal(), req.sortOrder(), req.active());
    }
}
