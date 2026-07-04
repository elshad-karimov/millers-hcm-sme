package az.millers.hcm.organization.service;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.organization.api.dto.LegalEntityDtos.LegalEntityRequest;
import az.millers.hcm.organization.api.dto.LegalEntityDtos.LegalEntityResponse;
import az.millers.hcm.organization.domain.LegalEntity;
import az.millers.hcm.organization.repo.LegalEntityRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * M140 — admin CRUD for the legal-entity master.
 *
 * <p>Contracts the SPA / audit pass lean on:
 * <ul>
 *   <li>Code is unique + immutable after create (mirrors letter-template
 *       behaviour).</li>
 *   <li>Encrypted bank-account plaintext only surfaces to
 *       {@code SYSTEM_ADMIN} / {@code HR_ADMIN}; everyone else gets a
 *       last-4 mask.</li>
 *   <li>Deactivate (status flip) instead of hard delete — historical
 *       payroll / letter references stay resolvable.</li>
 * </ul>
 */
@Service
public class LegalEntityService {

    private static final String MODULE = "ORGANIZATION";
    private static final String ENTITY = "LegalEntity";

    private static final java.util.Set<String> BANK_PLAINTEXT_ROLES = java.util.Set.of(
            "ROLE_SYSTEM_ADMIN", "ROLE_HR_ADMIN");

    private final LegalEntityRepository repo;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public LegalEntityService(LegalEntityRepository repo,
                               AuditService audit,
                               CurrentRequest currentRequest) {
        this.repo = repo;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<LegalEntity> list(boolean activeOnly) {
        return activeOnly
                ? repo.findByActiveTrueOrderByCodeAsc()
                : repo.findAllByOrderByCodeAsc();
    }

    @Transactional(readOnly = true)
    public LegalEntity get(UUID id) {
        return repo.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Legal entity not found: " + id));
    }

    @Transactional
    public LegalEntity create(LegalEntityRequest req) {
        String code = req.code().trim();
        if (repo.existsByCode(code)) {
            throw new BadRequestException("Legal entity code already exists: " + code);
        }
        LegalEntity e = new LegalEntity();
        e.setCode(code);
        apply(e, req);
        e.setCreatedBy(currentRequest.username());
        e.setUpdatedBy(currentRequest.username());
        LegalEntity saved = repo.save(e);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, LegalEntityResponse.from(saved, true));
        return saved;
    }

    @Transactional
    public LegalEntity update(UUID id, LegalEntityRequest req) {
        LegalEntity e = get(id);
        if (!e.getCode().equalsIgnoreCase(req.code())) {
            throw new BadRequestException("Legal entity code is immutable");
        }
        LegalEntityResponse before = LegalEntityResponse.from(e, true);
        apply(e, req);
        e.setUpdatedBy(currentRequest.username());
        LegalEntity saved = repo.save(e);
        audit.record(MODULE, ENTITY, id.toString(),
                "UPDATE", before, LegalEntityResponse.from(saved, true));
        return saved;
    }

    @Transactional
    public LegalEntity setActive(UUID id, boolean active) {
        LegalEntity e = get(id);
        if (e.isActive() == active) return e;
        LegalEntityResponse before = LegalEntityResponse.from(e, true);
        e.setActive(active);
        e.setUpdatedBy(currentRequest.username());
        LegalEntity saved = repo.save(e);
        audit.record(MODULE, ENTITY, id.toString(),
                active ? "REACTIVATE" : "DEACTIVATE",
                before, LegalEntityResponse.from(saved, true));
        return saved;
    }

    /**
     * True iff the caller's authorities include a role permitted to
     * see the unmasked payroll bank account. SPA + controllers should
     * consult this before passing the {@code canSeeBankPlain} flag to
     * {@link LegalEntityResponse#from}.
     */
    public boolean canSeeBankPlain() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        for (var ga : auth.getAuthorities()) {
            if (BANK_PLAINTEXT_ROLES.contains(ga.getAuthority())) return true;
        }
        return false;
    }

    /**
     * Same role gate as {@link #canSeeBankPlain()} but for the body
     * write path — keeps the bank-account ever-seen contract one-way:
     * only the same set of admins can update it.
     */
    public void assertCanWriteBank() {
        if (!canSeeBankPlain()) {
            throw new AccessDeniedException(
                    "Only HR_ADMIN / SYSTEM_ADMIN can edit payroll bank fields");
        }
    }

    private void apply(LegalEntity e, LegalEntityRequest req) {
        e.setName(req.name());
        e.setRegistrationNumber(req.registrationNumber());
        e.setTaxId(req.taxId());
        e.setSocialInsuranceRegNumber(req.socialInsuranceRegNumber());
        e.setLegalAddress(req.legalAddress());
        e.setCountry(req.country());
        e.setCurrency(req.currency());
        e.setFiscalCalendar(req.fiscalCalendar());
        e.setPayrollBankName(req.payrollBankName());
        // The bank-account write path is role-gated separately —
        // payroll account is sensitive PCI-adjacent data.
        if (req.payrollBankAccount() != null) {
            assertCanWriteBank();
            e.setPayrollBankAccount(req.payrollBankAccount());
        }
        e.setPayrollBankSwift(req.payrollBankSwift());
        e.setDefaultCostCentreCode(req.defaultCostCentreCode());
        e.setChartOfAccountsRef(req.chartOfAccountsRef());
        e.setLegalRepresentativeName(req.legalRepresentativeName());
        e.setLegalRepresentativeTitle(req.legalRepresentativeTitle());
        e.setCompanySealUrl(req.companySealUrl());
        e.setEffectiveFrom(req.effectiveFrom());
        e.setEffectiveTo(req.effectiveTo());
        e.setNotes(req.notes());
        if (req.active() != null) e.setActive(req.active());
    }
}
