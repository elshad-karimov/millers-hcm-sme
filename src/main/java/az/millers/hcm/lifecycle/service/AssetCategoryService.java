package az.millers.hcm.lifecycle.service;
import az.millers.hcm.common.tenant.TenantContext;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.lifecycle.domain.AssetCategory;
import az.millers.hcm.lifecycle.repo.AssetCategoryRepository;
import az.millers.hcm.security.CurrentRequest;

/** M456 — Asset category service. */
@Service
public class AssetCategoryService {
    private static final String MODULE = "lifecycle";
    private static final String ENTITY = "AssetCategory";

    private final AssetCategoryRepository repository;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public AssetCategoryService(AssetCategoryRepository repository, AuditService audit, CurrentRequest currentRequest) {
        this.repository = repository;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<AssetCategory> listActive() {
        return repository.findByTenantIdAndActiveOrderByName(TenantContext.current(), true);
    }

    @Transactional(readOnly = true)
    public List<AssetCategory> listAll() {
        return repository.findByTenantIdOrderByName(TenantContext.current());
    }

    @Transactional(readOnly = true)
    public AssetCategory get(UUID id) {
        return repository.findById(id)
                .filter(c -> TenantContext.current().equals(c.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Asset category not found"));
    }

    @Transactional
    public AssetCategory create(AssetCategory category) {
        category.setTenantId(TenantContext.current());
        category.setCreatedBy(currentRequest.username());
        category.setUpdatedBy(currentRequest.username());
        AssetCategory saved = repository.save(category);
        audit.record(MODULE, ENTITY, saved.getId().toString(), "CREATE", null, saved);
        return saved;
    }

    @Transactional
    public AssetCategory update(UUID id, AssetCategory req) {
        AssetCategory existing = get(id);
        existing.setCode(req.getCode());
        existing.setName(req.getName());
        existing.setDescription(req.getDescription());
        existing.setDefaultDepreciationMethod(req.getDefaultDepreciationMethod());
        existing.setDefaultUsefulLifeYears(req.getDefaultUsefulLifeYears());
        existing.setActive(req.getActive());
        existing.setUpdatedBy(currentRequest.username());
        AssetCategory saved = repository.save(existing);
        audit.record(MODULE, ENTITY, id.toString(), "UPDATE", null, saved);
        return saved;
    }
}
