package az.millers.hcm.corehr.service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.core.context.SecurityContextHolder;
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
    public List<DocumentCategory> listAll() {
        return repo.findAll().stream()
                .filter(c -> TENANT.equals(c.getTenantId()))
                .collect(Collectors.toList());
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

    @Transactional
    public DocumentCategory create(String code, String name, Boolean mandatory,
                                   Integer retentionDays, Boolean autoRenewal, Integer sortOrder) {
        String currentUser = SecurityContextHolder.getContext().getAuthentication().getName();

        DocumentCategory category = new DocumentCategory();
        category.setTenantId(TENANT);
        category.setCode(code);
        category.setName(name);
        category.setMandatory(mandatory != null ? mandatory : false);
        category.setRetentionDays(retentionDays);
        category.setAutoCreateRenewalRequest(autoRenewal != null ? autoRenewal : false);
        category.setSortOrder(sortOrder != null ? sortOrder : 0);
        category.setActive(true);
        category.setCreatedBy(currentUser);

        return repo.save(category);
    }

    @Transactional
    public DocumentCategory update(UUID id, String name, Boolean mandatory,
                                   Integer retentionDays, Boolean autoRenewal,
                                   Integer sortOrder, Boolean active) {
        DocumentCategory category = get(id);

        if (name != null) category.setName(name);
        if (mandatory != null) category.setMandatory(mandatory);
        if (retentionDays != null) category.setRetentionDays(retentionDays);
        if (autoRenewal != null) category.setAutoCreateRenewalRequest(autoRenewal);
        if (sortOrder != null) category.setSortOrder(sortOrder);
        if (active != null) category.setActive(active);

        return repo.save(category);
    }
}
