package az.millers.hcm.payroll.service;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.payroll.domain.LoanType;
import az.millers.hcm.payroll.repo.LoanTypeRepository;
import az.millers.hcm.security.CurrentRequest;

/** M460 — Loan type service. */
@Service
public class LoanTypeService {
    private static final String TENANT = "default";
    private static final String MODULE = "payroll";
    private static final String ENTITY = "LoanType";

    private final LoanTypeRepository repository;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public LoanTypeService(LoanTypeRepository repository, AuditService audit, CurrentRequest currentRequest) {
        this.repository = repository;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<LoanType> listActive() {
        return repository.findByTenantIdAndActiveOrderByName(TENANT, true);
    }

    @Transactional(readOnly = true)
    public List<LoanType> listAll() {
        return repository.findByTenantIdOrderByName(TENANT);
    }

    @Transactional(readOnly = true)
    public LoanType get(UUID id) {
        return repository.findById(id)
                .filter(t -> TENANT.equals(t.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Loan type not found"));
    }

    @Transactional
    public LoanType create(LoanType type) {
        type.setTenantId(TENANT);
        type.setCreatedBy(currentRequest.username());
        type.setUpdatedBy(currentRequest.username());
        LoanType saved = repository.save(type);
        audit.record(MODULE, ENTITY, saved.getId().toString(), "CREATE", null, saved);
        return saved;
    }

    @Transactional
    public LoanType update(UUID id, LoanType req) {
        LoanType existing = get(id);
        existing.setCode(req.getCode());
        existing.setName(req.getName());
        existing.setDescription(req.getDescription());
        existing.setMaxAmount(req.getMaxAmount());
        existing.setMaxMultipleOfNet(req.getMaxMultipleOfNet());
        existing.setMaxMonths(req.getMaxMonths());
        existing.setInterestRatePct(req.getInterestRatePct());
        existing.setMinTenureMonths(req.getMinTenureMonths());
        existing.setMaxActiveLoans(req.getMaxActiveLoans());
        existing.setActive(req.getActive());
        existing.setUpdatedBy(currentRequest.username());
        LoanType saved = repository.save(existing);
        audit.record(MODULE, ENTITY, id.toString(), "UPDATE", null, saved);
        return saved;
    }
}
