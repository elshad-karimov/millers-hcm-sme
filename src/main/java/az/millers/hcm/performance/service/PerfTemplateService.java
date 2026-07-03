package az.millers.hcm.performance.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.performance.api.dto.PerfTemplateDtos.SectionRequest;
import az.millers.hcm.performance.api.dto.PerfTemplateDtos.TemplateRequest;
import az.millers.hcm.performance.api.dto.PerfTemplateDtos.TemplateResponse;
import az.millers.hcm.performance.domain.PerfReviewTemplate;
import az.millers.hcm.performance.domain.PerfTemplateSection;
import az.millers.hcm.performance.repo.PerfReviewTemplateRepository;
import az.millers.hcm.performance.repo.PerfTemplateSectionRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * HCM_12 M389 — review template CRUD (PRD §5.2). Scoring sections' weights must sum to
 * 100 (§18.1 / §37.5); informational sections carry weight 0. Sections use full-replace
 * semantics (same editor pattern as plan tiers / checklist tasks).
 */
@Service
public class PerfTemplateService {

    private static final String TENANT = "default";
    private static final String MODULE = "PERFORMANCE";
    private static final String ENTITY = "PerfReviewTemplate";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final PerfReviewTemplateRepository templates;
    private final PerfTemplateSectionRepository sections;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public PerfTemplateService(PerfReviewTemplateRepository templates,
                               PerfTemplateSectionRepository sections,
                               AuditService audit,
                               CurrentRequest currentRequest) {
        this.templates = templates;
        this.sections = sections;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<TemplateResponse> list(boolean activeOnly) {
        List<PerfReviewTemplate> rows = activeOnly
                ? templates.findByTenantIdAndActiveTrueOrderByTemplateNameAsc(TENANT)
                : templates.findByTenantIdOrderByTemplateNameAsc(TENANT);
        return rows.stream()
                .map(t -> TemplateResponse.from(t, sections.findByTemplateIdOrderBySectionOrderAsc(t.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public TemplateResponse get(UUID id) {
        PerfReviewTemplate t = load(id);
        return TemplateResponse.from(t, sections.findByTemplateIdOrderBySectionOrderAsc(id));
    }

    private PerfReviewTemplate load(UUID id) {
        return templates.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review template not found: " + id));
    }

    @Transactional
    public TemplateResponse create(TemplateRequest req) {
        String code = req.templateCode().trim().toUpperCase();
        if (templates.existsByTenantIdAndTemplateCode(TENANT, code)) {
            throw new BadRequestException("Template code already exists: " + code);
        }
        validateSections(req.sections());
        PerfReviewTemplate t = new PerfReviewTemplate();
        t.setTenantId(TENANT);
        t.setTemplateCode(code);
        apply(t, req);
        t.setCreatedBy(currentRequest.username());
        PerfReviewTemplate saved = templates.save(t);
        replaceSections(saved.getId(), req.sections());
        TemplateResponse response = get(saved.getId());
        audit.record(MODULE, ENTITY, saved.getId().toString(), "CREATE", null, response);
        return response;
    }

    @Transactional
    public TemplateResponse update(UUID id, TemplateRequest req) {
        PerfReviewTemplate t = load(id);
        TemplateResponse before = get(id);
        String code = req.templateCode().trim().toUpperCase();
        if (!t.getTemplateCode().equals(code) && templates.existsByTenantIdAndTemplateCode(TENANT, code)) {
            throw new BadRequestException("Template code already exists: " + code);
        }
        validateSections(req.sections());
        t.setTemplateCode(code);
        apply(t, req);
        templates.save(t);
        replaceSections(id, req.sections());
        TemplateResponse response = get(id);
        audit.record(MODULE, ENTITY, id.toString(), "UPDATE", before, response);
        return response;
    }

    private void apply(PerfReviewTemplate t, TemplateRequest req) {
        t.setTemplateName(req.templateName());
        t.setDescription(req.description());
        t.setLegalEntityId(req.legalEntityId());
        t.setDepartmentId(req.departmentId());
        t.setGradeId(req.gradeId());
        t.setEmployeeType(req.employeeType() == null || req.employeeType().isBlank()
                ? null : req.employeeType().trim());
        t.setActive(req.active() == null ? true : req.active());
    }

    private void replaceSections(UUID templateId, List<SectionRequest> reqs) {
        sections.deleteByTemplateId(templateId);
        sections.flush();
        int order = 1;
        for (SectionRequest r : reqs) {
            PerfTemplateSection s = new PerfTemplateSection();
            s.setTenantId(TENANT);
            s.setTemplateId(templateId);
            s.setSectionType(r.sectionType());
            s.setSectionOrder(order++);
            s.setTitle(r.title());
            s.setWeightPercent(r.weightPercent() == null ? BigDecimal.ZERO : r.weightPercent());
            s.setRequired(r.required() == null ? true : r.required());
            sections.save(s);
        }
    }

    /** §18.1 / §37.5 — scoring sections' weights must total exactly 100. */
    private static void validateSections(List<SectionRequest> reqs) {
        if (reqs == null || reqs.isEmpty()) {
            throw new BadRequestException("A template needs at least one section");
        }
        BigDecimal scoringTotal = reqs.stream()
                .filter(r -> r.sectionType().isScoring())
                .map(r -> r.weightPercent() == null ? BigDecimal.ZERO : r.weightPercent())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (scoringTotal.compareTo(BigDecimal.ZERO) > 0 && scoringTotal.compareTo(HUNDRED) != 0) {
            throw new BadRequestException(
                    "Scoring section weights must total 100% (currently " + scoringTotal + "%)");
        }
    }
}
