package az.millers.hcm.corehr.service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.DocumentCategory;
import az.millers.hcm.corehr.repo.DocumentCategoryRepository;

/**
 * M439 — Document category service.
 */
@Service
public class DocumentCategoryService {

    private static final String TENANT = "default";

    private final DocumentCategoryRepository repo;

    public DocumentCategoryService(DocumentCategoryRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<DocumentCategory> listActive() {
        return repo.findByTenantIdAndActiveTrueOrderBySortOrderAsc(TENANT)
                .stream()
                .filter(c -> TENANT.equals(c.getTenantId()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DocumentCategory get(UUID id) {
        DocumentCategory category = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document category not found: " + id));
        if (!TENANT.equals(category.getTenantId())) {
            throw new ResourceNotFoundException("Document category not found: " + id);
        }
        return category;
    }
}
