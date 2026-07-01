package az.millers.hcm.compensation.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.compensation.api.dto.CompConfigDto;
import az.millers.hcm.compensation.domain.BudgetExceededPolicy;
import az.millers.hcm.compensation.domain.CompConfig;
import az.millers.hcm.compensation.repo.CompConfigRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * M359 — Tenant compensation configuration service.
 * One config per tenant; creates default if missing.
 */
@Service
public class CompConfigService {

    private static final String MODULE = "compensation";
    private static final String ENTITY = "CompConfig";
    private static final String TENANT = "default";

    private final CompConfigRepository repo;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public CompConfigService(CompConfigRepository repo,
                             AuditService audit,
                             CurrentRequest currentRequest) {
        this.repo = repo;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public CompConfig get() {
        return repo.findByTenantId(TENANT).orElseGet(this::createDefault);
    }

    @Transactional
    public CompConfig update(BigDecimal maxIncreasePct,
                              BudgetExceededPolicy policy,
                              String currency) {
        CompConfig cfg = get();
        CompConfigDto before = CompConfigDto.from(cfg);

        cfg.setMaxIncreasePctWithoutApproval(maxIncreasePct);
        cfg.setBudgetExceededPolicy(policy);
        cfg.setDefaultCurrency(currency);

        CompConfig saved = repo.save(cfg);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "UPDATE", before, CompConfigDto.from(saved));
        return saved;
    }

    private CompConfig createDefault() {
        CompConfig cfg = new CompConfig();
        cfg.setTenantId(TENANT);
        return repo.save(cfg);
    }
}
