package az.millers.hcm.compbenefits.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.compbenefits.api.dto.BenefitProviderDtos.ProviderRequest;
import az.millers.hcm.compbenefits.api.dto.BenefitProviderDtos.ProviderResponse;
import az.millers.hcm.compbenefits.domain.BenefitProvider;
import az.millers.hcm.compbenefits.repo.BenefitProviderRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * HCM_11 M374 — Benefit provider / vendor master CRUD. Tenant-scoped, audited.
 */
@Service
public class BenefitProviderService {

    private static final String TENANT = "default";
    private static final String MODULE = "COMP_BENEFITS";
    private static final String ENTITY = "BenefitProvider";

    private final BenefitProviderRepository repo;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public BenefitProviderService(BenefitProviderRepository repo,
                                  AuditService audit,
                                  CurrentRequest currentRequest) {
        this.repo = repo;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<ProviderResponse> list(boolean activeOnly) {
        List<BenefitProvider> rows = activeOnly
                ? repo.findByTenantIdAndActiveTrueOrderByNameAsc(TENANT)
                : repo.findByTenantIdOrderByNameAsc(TENANT);
        return rows.stream().map(ProviderResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public BenefitProvider get(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Benefit provider not found: " + id));
    }

    @Transactional
    public ProviderResponse create(ProviderRequest req) {
        String code = normalizeCode(req.code());
        if (repo.existsByTenantIdAndCode(TENANT, code)) {
            throw new BadRequestException("Benefit provider code already exists: " + code);
        }
        validateContract(req);
        BenefitProvider p = new BenefitProvider();
        p.setTenantId(TENANT);
        p.setCode(code);
        apply(p, req);
        p.setCreatedBy(currentRequest.username());
        BenefitProvider saved = repo.save(p);
        ProviderResponse response = ProviderResponse.from(saved);
        audit.record(MODULE, ENTITY, saved.getId().toString(), "CREATE", null, response);
        return response;
    }

    @Transactional
    public ProviderResponse update(UUID id, ProviderRequest req) {
        BenefitProvider p = get(id);
        ProviderResponse before = ProviderResponse.from(p);
        String code = normalizeCode(req.code());
        if (!p.getCode().equals(code) && repo.existsByTenantIdAndCode(TENANT, code)) {
            throw new BadRequestException("Benefit provider code already exists: " + code);
        }
        validateContract(req);
        p.setCode(code);
        apply(p, req);
        BenefitProvider saved = repo.save(p);
        ProviderResponse response = ProviderResponse.from(saved);
        audit.record(MODULE, ENTITY, id.toString(), "UPDATE", before, response);
        return response;
    }

    private void apply(BenefitProvider p, ProviderRequest req) {
        p.setName(req.name());
        p.setProviderType(req.providerType());
        p.setContactName(req.contactName());
        p.setContactEmail(req.contactEmail());
        p.setContactPhone(req.contactPhone());
        p.setWebsite(req.website());
        p.setContractNo(req.contractNo());
        p.setContractStart(req.contractStart());
        p.setContractEnd(req.contractEnd());
        p.setNotes(req.notes());
        p.setActive(req.active() == null ? true : req.active());
    }

    private static void validateContract(ProviderRequest req) {
        if (req.contractStart() != null && req.contractEnd() != null
                && req.contractEnd().isBefore(req.contractStart())) {
            throw new BadRequestException("contractEnd must be on or after contractStart");
        }
    }

    private static String normalizeCode(String code) {
        return code == null ? null : code.trim().toUpperCase();
    }
}
