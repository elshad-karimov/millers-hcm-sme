package az.millers.hcm.payroll.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.payroll.api.dto.BankAccountRequest;
import az.millers.hcm.payroll.api.dto.BankAccountResponse;
import az.millers.hcm.payroll.domain.BankAccount;
import az.millers.hcm.payroll.repo.BankAccountRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.PiiAccessRoles;

/**
 * Manages employee bank accounts (M74 / P2-06). Migrated from a 1:1
 * "single account per employee" model to multi-row with a salary split.
 *
 * <p>The legacy {@link #upsert(BankAccount)} signature is preserved as a
 * compatibility shim — pre-M74 callers (the payroll workflow listener,
 * batch test fixtures) still work without modification. The new
 * {@link #create(BankAccountRequest)} / {@link #update(UUID, BankAccountRequest)}
 * path drives the multi-row functionality.
 *
 * <p>Split-percent invariant: the sum of {@code salarySplitPercent} across
 * an employee's active accounts must equal 100. Enforced application-side
 * via {@link BankAccountRepository#sumActiveSplitForEmployee}. The primary
 * flag is mutually exclusive — promoting one account to primary demotes
 * the previous one in the same TX, mirroring the M63 emergency-contact
 * promote-primary pattern.
 */
@Service
public class BankAccountService {

    private static final String MODULE = "PAYROLL";
    private static final String ENTITY = "BankAccount";

    private final BankAccountRepository repository;
    private final EmployeeRepository employees;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public BankAccountService(BankAccountRepository repository,
                               EmployeeRepository employees,
                               AuditService audit,
                               CurrentRequest currentRequest) {
        this.repository = repository;
        this.employees = employees;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    // ── Reads ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<BankAccountResponse> listForEmployee(UUID employeeId, boolean activeOnly) {
        boolean canSeePlain = PiiAccessRoles.callerCanSeePlaintextPii();
        List<BankAccount> rows = activeOnly
                ? repository.findActiveByEmployeeId(employeeId)
                : repository.findByEmployeeIdOrderByPrimaryDescCreatedAtAsc(employeeId);
        return rows.stream().map(a -> BankAccountResponse.from(a, canSeePlain)).toList();
    }

    @Transactional(readOnly = true)
    public Optional<BankAccount> findForEmployee(UUID employeeId) {
        // Pre-M74 single-account accessor — returns the primary active account
        // so payroll bank-file generation keeps working.
        return repository.findByEmployeeId(employeeId);
    }

    // ── Writes (new multi-account API) ────────────────────────────────────────

    @Transactional
    public BankAccountResponse create(BankAccountRequest req) {
        if (!employees.existsById(req.employeeId())) {
            throw new BadRequestException("Employee not found: " + req.employeeId());
        }
        BankAccount a = new BankAccount();
        a.setEmployeeId(req.employeeId());
        applyRequest(a, req);
        a.setCreatedBy(currentRequest.username());
        a.setUpdatedBy(currentRequest.username());
        validateSplit(a, null);

        // If the new row is primary, demote the prior primary inside the same TX.
        if (a.isPrimary()) {
            demoteExistingPrimary(a.getEmployeeId(), null);
        }
        BankAccount saved = repository.save(a);

        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, BankAccountResponse.from(saved, true));
        return BankAccountResponse.from(saved, PiiAccessRoles.callerCanSeePlaintextPii());
    }

    @Transactional
    public BankAccountResponse update(UUID id, BankAccountRequest req) {
        BankAccount a = repository.findById(id).orElseThrow(() ->
                new BadRequestException("Bank account not found: " + id));
        if (!a.getEmployeeId().equals(req.employeeId())) {
            throw new BadRequestException(
                    "employeeId in payload (" + req.employeeId()
                            + ") does not match the bank account's owner (" + a.getEmployeeId() + ")");
        }
        BankAccountResponse before = BankAccountResponse.from(a, true);
        boolean wasPrimary = a.isPrimary();

        applyRequest(a, req);
        a.setUpdatedBy(currentRequest.username());
        validateSplit(a, id);

        if (a.isPrimary() && !wasPrimary) {
            demoteExistingPrimary(a.getEmployeeId(), id);
        }
        BankAccount saved = repository.save(a);

        audit.record(MODULE, ENTITY, id.toString(),
                "UPDATE", before, BankAccountResponse.from(saved, true));
        return BankAccountResponse.from(saved, PiiAccessRoles.callerCanSeePlaintextPii());
    }

    @Transactional
    public void delete(UUID id) {
        BankAccount a = repository.findById(id).orElseThrow(() ->
                new BadRequestException("Bank account not found: " + id));
        BankAccountResponse before = BankAccountResponse.from(a, true);
        repository.delete(a);
        audit.record(MODULE, ENTITY, id.toString(), "DELETE", before, null);
    }

    // ── Legacy compatibility — pre-M74 upsert path ────────────────────────────

    /**
     * Compatibility shim — pre-M74 callers send a full BankAccount entity and
     * expect 1:1 semantics. We map it onto the new model: if an active primary
     * exists for the employee, update it in place; otherwise insert a new
     * primary row with salary_split_percent=100.
     */
    @Transactional
    public BankAccount upsert(BankAccount incoming) {
        if (!employees.existsById(incoming.getEmployeeId())) {
            throw new BadRequestException("Employee not found: " + incoming.getEmployeeId());
        }
        BankAccount target = repository.findByEmployeeId(incoming.getEmployeeId())
                .orElseGet(BankAccount::new);
        target.setEmployeeId(incoming.getEmployeeId());
        target.setBankCode(incoming.getBankCode());
        target.setBankName(incoming.getBankName());
        target.setIban(incoming.getIban());
        target.setAccountNumber(incoming.getAccountNumber());
        if (incoming.getSwiftBic() != null) {
            target.setSwiftBic(incoming.getSwiftBic());
        }
        target.setCurrency(incoming.getCurrency() == null ? "AZN" : incoming.getCurrency());
        target.setActive(incoming.isActive());
        target.setPrimary(true);
        target.setSalarySplitPercent(new BigDecimal("100.00"));
        target.setUpdatedBy(currentRequest.username());
        if (target.getCreatedBy() == null) {
            target.setCreatedBy(currentRequest.username());
        }
        BankAccount saved = repository.save(target);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "UPSERT", null,
                Map.of("employeeId", saved.getEmployeeId().toString(),
                        "bankCode", saved.getBankCode() == null ? "" : saved.getBankCode(),
                        "primary", saved.isPrimary()));
        return saved;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void applyRequest(BankAccount a, BankAccountRequest req) {
        a.setBankCode(req.bankCode());
        a.setBankName(req.bankName());
        a.setIban(req.iban());
        a.setAccountNumber(req.accountNumber());
        a.setSwiftBic(req.swiftBic() == null ? null : req.swiftBic().toUpperCase());
        a.setCurrency(req.currency() == null ? "AZN" : req.currency().toUpperCase());
        a.setSalarySplitPercent(req.salarySplitPercent() != null
                ? req.salarySplitPercent() : new BigDecimal("100.00"));
        a.setPrimary(req.primary() == null || req.primary());
        a.setActive(req.active() == null || req.active());
        a.setNotes(req.notes());
    }

    /**
     * Ensure the employee's active accounts sum to exactly 100% after this
     * change. {@code excludeId} skips the row being updated so its NEW split
     * is the one counted.
     */
    private void validateSplit(BankAccount candidate, UUID excludeId) {
        if (!candidate.isActive()) return;     // inactive rows don't count
        BigDecimal sumOfOthers = repository
                .sumActiveSplitForEmployee(candidate.getEmployeeId(), excludeId);
        BigDecimal total = sumOfOthers.add(candidate.getSalarySplitPercent());
        if (total.compareTo(new BigDecimal("100.00")) != 0) {
            throw new BadRequestException(
                    "Active bank account split for employee " + candidate.getEmployeeId()
                            + " must sum to 100.00 (currently would be "
                            + total.toPlainString() + ").");
        }
    }

    /**
     * Demote any other primary account for the employee — partial unique
     * index in V60 enforces "at most one primary"; this keeps the swap
     * atomic within one transaction.
     */
    private void demoteExistingPrimary(UUID employeeId, UUID excludeId) {
        repository.findByEmployeeId(employeeId).ifPresent(prior -> {
            if (excludeId != null && prior.getId().equals(excludeId)) return;
            prior.setPrimary(false);
            prior.setUpdatedBy(currentRequest.username());
            repository.save(prior);
            repository.flush();
        });
    }
}
