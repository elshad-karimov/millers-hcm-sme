package az.millers.hcm.compliance.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.compliance.domain.StatutoryReportTemplate;
import az.millers.hcm.compliance.repo.StatutoryReportTemplateRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * M468 — Statutory report template CRUD.
 */
@Service
public class StatutoryReportTemplateService {

    private final StatutoryReportTemplateRepository templates;
    private final CurrentRequest currentRequest;

    public StatutoryReportTemplateService(StatutoryReportTemplateRepository templates,
                                           CurrentRequest currentRequest) {
        this.templates = templates;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<StatutoryReportTemplate> listActive() {
        return templates.findByTenantIdAndActiveOrderByCodeAsc("default", true);
    }

    @Transactional(readOnly = true)
    public StatutoryReportTemplate get(UUID id) {
        StatutoryReportTemplate template = templates.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Statutory report template not found: " + id));

        if (!"default".equals(template.getTenantId())) {
            throw new ResourceNotFoundException("Statutory report template not found: " + id);
        }

        return template;
    }

    @Transactional
    public StatutoryReportTemplate create(StatutoryReportTemplate template) {
        String tenantId = "default";

        if (templates.findByTenantIdAndCode(tenantId, template.getCode()).isPresent()) {
            throw new BadRequestException("TEMPLATE_CODE_EXISTS");
        }

        template.setTenantId(tenantId);
        template.setCreatedBy(currentRequest.username());
        template.setUpdatedBy(currentRequest.username());

        return templates.save(template);
    }

    @Transactional
    public StatutoryReportTemplate update(UUID id, StatutoryReportTemplate update) {
        StatutoryReportTemplate existing = get(id);

        existing.setName(update.getName());
        existing.setCountry(update.getCountry());
        existing.setFrequency(update.getFrequency());
        existing.setFileFormat(update.getFileFormat());
        existing.setDueDay(update.getDueDay());
        existing.setDescription(update.getDescription());
        existing.setActive(update.isActive());
        existing.setUpdatedBy(currentRequest.username());
        existing.setUpdatedAt(OffsetDateTime.now());

        return templates.save(existing);
    }

    @Transactional
    public void delete(UUID id) {
        StatutoryReportTemplate template = get(id);
        templates.delete(template);
    }
}
