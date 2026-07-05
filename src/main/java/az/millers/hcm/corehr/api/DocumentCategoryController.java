package az.millers.hcm.corehr.api;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<DocumentCategory> listActive() {
        return service.listActive();
    }
}
