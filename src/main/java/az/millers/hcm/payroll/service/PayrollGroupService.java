package az.millers.hcm.payroll.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.payroll.api.dto.PayrollGroupRequest;
import az.millers.hcm.payroll.api.dto.PayrollGroupResponse;
import az.millers.hcm.payroll.domain.BankFileFormat;
import az.millers.hcm.payroll.domain.PayCycle;
import az.millers.hcm.payroll.domain.PayrollGroup;
import az.millers.hcm.payroll.repo.PayrollGroupRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * Master CRUD for {@link PayrollGroup} (M75 / P2-19). One row must remain the
 * default (used when {@code Employee.payrollGroupId} is NULL); setting a new
 * default automatically demotes the prior one.
 */
@Service
public class PayrollGroupService {

    private static final String MODULE = "PAYROLL";
    private static final String ENTITY = "PayrollGroup";

    private final PayrollGroupRepository repo;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public PayrollGroupService(PayrollGroupRepository repo,
                                AuditService audit,
                                CurrentRequest currentRequest) {
        this.repo = repo;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<PayrollGroup> list(boolean activeOnly) {
        return activeOnly
                ? repo.findByActiveTrueOrderByNameAsc()
                : repo.findAll();
    }

    @Transactional(readOnly = true)
    public PayrollGroup get(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PayrollGroup not found: " + id));
    }

    /**
     * Returns the effective payroll group for an employee given their (possibly
     * null) {@code payrollGroupId}. Falls back to the seeded default — same
     * override-or-fallback pattern as {@code LeaveGroupService} (M66).
     */
    @Transactional(readOnly = true)
    public PayrollGroup resolveFor(UUID employeePayrollGroupId) {
        if (employeePayrollGroupId != null) {
            return get(employeePayrollGroupId);
        }
        return repo.findFirstByDefaultGroupTrue()
                .orElseThrow(() -> new IllegalStateException(
                        "No default PayrollGroup configured — seed migration missing?"));
    }

    @Transactional
    public PayrollGroup create(PayrollGroupRequest req) {
        String code = normaliseCode(req.code());
        if (repo.findByCode(code).isPresent()) {
            throw new BadRequestException("PayrollGroup code already exists: " + code);
        }
        PayrollGroup g = new PayrollGroup();
        g.setCode(code);
        applyRequest(g, req);
        if (Boolean.TRUE.equals(req.defaultGroup())) {
            demoteCurrentDefault();
            g.setDefaultGroup(true);
        }
        g.setCreatedBy(currentRequest.username());
        g.setUpdatedBy(currentRequest.username());
        PayrollGroup saved = repo.save(g);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, PayrollGroupResponse.from(saved));
        return saved;
    }

    @Transactional
    public PayrollGroup update(UUID id, PayrollGroupRequest req) {
        PayrollGroup g = get(id);
        PayrollGroupResponse before = PayrollGroupResponse.from(g);
        if (!g.getCode().equalsIgnoreCase(req.code())) {
            throw new BadRequestException("PayrollGroup code is immutable");
        }
        applyRequest(g, req);
        if (Boolean.TRUE.equals(req.defaultGroup()) && !g.isDefaultGroup()) {
            demoteCurrentDefault();
            g.setDefaultGroup(true);
        } else if (Boolean.FALSE.equals(req.defaultGroup()) && g.isDefaultGroup()) {
            throw new BadRequestException(
                    "Cannot un-set default — promote another PayrollGroup to default instead");
        }
        g.setUpdatedBy(currentRequest.username());
        PayrollGroup saved = repo.save(g);
        audit.record(MODULE, ENTITY, id.toString(),
                "UPDATE", before, PayrollGroupResponse.from(saved));
        return saved;
    }

    @Transactional
    public void delete(UUID id) {
        PayrollGroup g = get(id);
        if (g.isDefaultGroup()) {
            throw new BadRequestException("Cannot deactivate the default PayrollGroup");
        }
        PayrollGroupResponse before = PayrollGroupResponse.from(g);
        g.setActive(false);
        g.setUpdatedBy(currentRequest.username());
        repo.save(g);
        audit.record(MODULE, ENTITY, id.toString(),
                "DEACTIVATE", before, PayrollGroupResponse.from(g));
    }

    private void demoteCurrentDefault() {
        repo.findFirstByDefaultGroupTrue().ifPresent(existing -> {
            existing.setDefaultGroup(false);
            existing.setUpdatedBy(currentRequest.username());
            repo.save(existing);
        });
    }

    private void applyRequest(PayrollGroup g, PayrollGroupRequest req) {
        g.setName(req.name());
        g.setDescription(req.description());
        g.setPayCycle(req.payCycle() == null ? PayCycle.MONTHLY : req.payCycle());
        g.setBankFileFormat(req.bankFileFormat() == null ? BankFileFormat.CSV : req.bankFileFormat());
        g.setDefaultCurrency(StringUtils.hasText(req.defaultCurrency())
                ? req.defaultCurrency().toUpperCase() : "AZN");
        g.setRulesJson(req.rulesJson());
        if (req.active() != null) g.setActive(req.active());
    }

    private static String normaliseCode(String code) {
        return code == null ? null : code.trim().toUpperCase();
    }
}
