package az.millers.hcm.analytics.service;

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

    private static final String TENANT = "default";

    private final KpiDefinitionRepository repository;

    public KpiDefinitionService(KpiDefinitionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<KpiDefinition> listActive() {
        return repository.findByTenantIdAndActiveOrderByCategory(TENANT, true);
    }

    @Transactional(readOnly = true)
    public List<KpiDefinition> listAll() {
        return repository.findByTenantIdAndActiveOrderByCategory(TENANT, null);
    }

    @Transactional(readOnly = true)
    public KpiDefinition get(UUID id) {
        return repository.findById(id)
                .filter(kpi -> TENANT.equals(kpi.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("KPI definition not found"));
    }

    @Transactional
    public KpiDefinition create(KpiDefinition kpi) {
        kpi.setTenantId(TENANT);
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
