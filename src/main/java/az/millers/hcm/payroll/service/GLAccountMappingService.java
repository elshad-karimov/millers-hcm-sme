package az.millers.hcm.payroll.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.payroll.domain.GLAccountMapping;
import az.millers.hcm.payroll.domain.GLAccountType;
import az.millers.hcm.payroll.repo.GLAccountMappingRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * M355 — GL account mapping service.
 */
@Service
public class GLAccountMappingService {

    private static final String MODULE = "PAYROLL";
    private static final String ENTITY = "GLAccountMapping";

    private final GLAccountMappingRepository mappings;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public GLAccountMappingService(GLAccountMappingRepository mappings,
                                    AuditService audit,
                                    CurrentRequest currentRequest) {
        this.mappings = mappings;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<GLAccountMapping> list() {
        String tenantId = "default";
        return mappings.findByTenantIdAndIsActiveTrueOrderByComponentKindAscComponentCodeAsc(tenantId);
    }

    @Transactional
    public GLAccountMapping create(MappingRequest req) {
        String tenantId = "default";

        GLAccountMapping mapping = new GLAccountMapping();
        mapping.setTenantId(tenantId);
        mapping.setComponentKind(req.componentKind());
        mapping.setComponentCode(req.componentCode());
        mapping.setAccountType(req.accountType());
        mapping.setGlAccountCode(req.glAccountCode());
        mapping.setGlAccountName(req.glAccountName());
        mapping.setIsActive(true);

        mappings.save(mapping);

        audit.record(MODULE, ENTITY, mapping.getId().toString(), "CREATED",
                null, req.componentKind() + "/" + req.componentCode() + " -> " + req.glAccountCode());

        return mapping;
    }

    /**
     * Resolve mapping for a component kind/code + account type.
     * Prefers exact match (kind + code), falls back to kind-only (code=null).
     */
    @Transactional(readOnly = true)
    public Optional<GLAccountMapping> resolve(String componentKind, String componentCode,
                                                GLAccountType accountType) {
        String tenantId = "default";

        // Try exact match first
        if (componentCode != null) {
            Optional<GLAccountMapping> exact = mappings.findExactMatch(
                    tenantId, componentKind, componentCode, accountType);
            if (exact.isPresent()) {
                return exact;
            }
        }

        // Fall back to kind-only
        return mappings.findKindFallback(tenantId, componentKind, accountType);
    }

    public record MappingRequest(String componentKind, String componentCode,
                                   GLAccountType accountType, String glAccountCode,
                                   String glAccountName) {}
}
