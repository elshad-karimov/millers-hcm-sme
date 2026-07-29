package az.millers.hcm.analytics.service;
import az.millers.hcm.common.tenant.TenantContext;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.analytics.domain.KpiDefinition;
import az.millers.hcm.analytics.repo.KpiDefinitionRepository;
import az.millers.hcm.common.ResourceNotFoundException;

/**
 * M473 — KPI definition CRUD service.
 */
@Service
public class KpiDefinitionService {


    private final KpiDefinitionRepository repository;

    public KpiDefinitionService(KpiDefinitionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<KpiDefinition> listActive() {
        return repository.findByTenantIdAndActiveOrderByCategory(TenantContext.current(), true);
    }

    @Transactional(readOnly = true)
    public List<KpiDefinition> listAll() {
        return repository.findByTenantIdAndActiveOrderByCategory(TenantContext.current(), null);
    }

    @Transactional(readOnly = true)
    public KpiDefinition get(UUID id) {
        return repository.findById(id)
                .filter(kpi -> TenantContext.current().equals(kpi.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("KPI definition not found"));
    }

    @Transactional
    public KpiDefinition create(KpiDefinition kpi) {
        kpi.setTenantId(TenantContext.current());
        return repository.save(kpi);
    }

    @Transactional
    public KpiDefinition update(UUID id, KpiDefinition updates) {
        KpiDefinition existing = get(id);
        existing.setName(updates.getName());
        existing.setCategory(updates.getCategory());
        existing.setDescription(updates.getDescription());
        existing.setUnit(updates.getUnit());
        existing.setTargetValue(updates.getTargetValue());
        existing.setActive(updates.getActive());
        return repository.save(existing);
    }

    @Transactional
    public void delete(UUID id) {
        KpiDefinition kpi = get(id);
        repository.delete(kpi);
    }
}
