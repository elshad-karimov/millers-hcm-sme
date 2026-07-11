package az.millers.hcm.reporting.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.reporting.domain.ReportDefinition;
import az.millers.hcm.reporting.domain.ReportFormat;
import az.millers.hcm.reporting.domain.ReportType;
import az.millers.hcm.reporting.repo.ReportDefinitionRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.common.BusinessNumbers;

@Service
public class ReportDefinitionService {

    private static final String MODULE = "REPORTING";
    private static final String ENTITY = "ReportDefinition";

    private final ReportDefinitionRepository repository;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public ReportDefinitionService(ReportDefinitionRepository repository,
                                    AuditService audit,
                                    CurrentRequest currentRequest) {
        this.repository = repository;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<ReportDefinition> list(ReportType type, Boolean activeOnly) {
        if (Boolean.TRUE.equals(activeOnly)) return repository.findByActiveTrueOrderByNameAsc();
        if (type != null) return repository.findByReportTypeOrderByNameAsc(type);
        return repository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public ReportDefinition get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Definition not found: " + id));
    }

    @Transactional
    public ReportDefinition create(String name, ReportType type, ReportFormat format,
                                     Map<String, Object> params, String description, Boolean active) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("name is required");
        }
        if (type == null) throw new BadRequestException("reportType is required");
        ReportDefinition d = new ReportDefinition();
        d.setDefinitionNo(BusinessNumbers.format("RDF", 5, repository.nextNoSequence()));
        d.setName(name);
        d.setReportType(type);
        d.setDefaultFormat(format == null ? ReportFormat.XLSX : format);
        d.setParameters(params == null ? new java.util.LinkedHashMap<>() : params);
        d.setDescription(description);
        d.setActive(active == null ? true : active);
        d.setCreatedBy(currentRequest.username());
        d.setUpdatedBy(currentRequest.username());
        ReportDefinition saved = repository.save(d);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, Map.of(
                        "definitionNo", saved.getDefinitionNo(),
                        "type", type.name(),
                        "format", saved.getDefaultFormat().name()));
        return saved;
    }

    @Transactional
    public ReportDefinition update(UUID id, String name, ReportType type, ReportFormat format,
                                     Map<String, Object> params, String description, Boolean active) {
        ReportDefinition d = get(id);
        if (name != null && !name.isBlank()) d.setName(name);
        if (type != null) d.setReportType(type);
        if (format != null) d.setDefaultFormat(format);
        if (params != null) d.setParameters(params);
        if (description != null) d.setDescription(description);
        if (active != null) d.setActive(active);
        d.setUpdatedBy(currentRequest.username());
        ReportDefinition saved = repository.save(d);
        audit.record(MODULE, ENTITY, id.toString(),
                "UPDATE", null, Map.of("definitionNo", saved.getDefinitionNo()));
        return saved;
    }
}
