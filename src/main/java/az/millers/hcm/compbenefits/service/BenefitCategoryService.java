package az.millers.hcm.compbenefits.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.compbenefits.api.dto.BenefitCategoryDtos.CategoryRequest;
import az.millers.hcm.compbenefits.api.dto.BenefitCategoryDtos.CategoryResponse;
import az.millers.hcm.compbenefits.domain.BenefitCategory;
import az.millers.hcm.compbenefits.repo.BenefitCategoryRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * HCM_11 M373 — Benefit category master CRUD. Tenant-scoped ('default'); all
 * mutations audited. Categories are referenced by benefit plans (M375) and drive
 * taxability / provider-requirement defaults.
 */
@Service
public class BenefitCategoryService {

    private static final String TENANT = "default";
    private static final String MODULE = "COMP_BENEFITS";
    private static final String ENTITY = "BenefitCategory";

    private final BenefitCategoryRepository repo;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public BenefitCategoryService(BenefitCategoryRepository repo,
                                  AuditService audit,
                                  CurrentRequest currentRequest) {
        this.repo = repo;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(boolean activeOnly) {
        List<BenefitCategory> rows = activeOnly
                ? repo.findByTenantIdAndActiveTrueOrderByDisplayOrderAscNameAsc(TENANT)
                : repo.findByTenantIdOrderByDisplayOrderAscNameAsc(TENANT);
        return rows.stream().map(CategoryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public BenefitCategory get(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Benefit category not found: " + id));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest req) {
        String code = normalizeCode(req.code());
        if (repo.existsByTenantIdAndCode(TENANT, code)) {
            throw new BadRequestException("Benefit category code already exists: " + code);
        }
        BenefitCategory c = new BenefitCategory();
        c.setTenantId(TENANT);
        c.setCode(code);
        apply(c, req);
        c.setCreatedBy(currentRequest.username());
        BenefitCategory saved = repo.save(c);
        CategoryResponse response = CategoryResponse.from(saved);
        audit.record(MODULE, ENTITY, saved.getId().toString(), "CREATE", null, response);
        return response;
    }

    @Transactional
    public CategoryResponse update(UUID id, CategoryRequest req) {
        BenefitCategory c = get(id);
        CategoryResponse before = CategoryResponse.from(c);
        String code = normalizeCode(req.code());
        if (!c.getCode().equals(code) && repo.existsByTenantIdAndCode(TENANT, code)) {
            throw new BadRequestException("Benefit category code already exists: " + code);
        }
        c.setCode(code);
        apply(c, req);
        BenefitCategory saved = repo.save(c);
        CategoryResponse response = CategoryResponse.from(saved);
        audit.record(MODULE, ENTITY, id.toString(), "UPDATE", before, response);
        return response;
    }

    private void apply(BenefitCategory c, CategoryRequest req) {
        c.setName(req.name());
        c.setDescription(req.description());
        c.setTaxable(Boolean.TRUE.equals(req.taxable()));
        c.setRequiresProvider(Boolean.TRUE.equals(req.requiresProvider()));
        c.setDisplayOrder(req.displayOrder() == null ? 0 : req.displayOrder());
        c.setActive(req.active() == null ? true : req.active());
    }

    private static String normalizeCode(String code) {
        return code == null ? null : code.trim().toUpperCase();
    }
}
