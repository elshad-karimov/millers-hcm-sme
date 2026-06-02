package az.millers.hcm.lifecycle.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.lifecycle.api.dto.ContractRequest;
import az.millers.hcm.lifecycle.api.dto.ContractResponse;
import az.millers.hcm.lifecycle.domain.ContractStatus;
import az.millers.hcm.lifecycle.domain.EmploymentContract;
import az.millers.hcm.lifecycle.repo.EmploymentContractRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.scope.AccessScopeService;

/**
 * Manages the {@link EmploymentContract} lifecycle (M64 / P1-03).
 *
 * <p>State machine implemented here:
 * <pre>
 *   create()    → DRAFT
 *   activate()  → ACTIVE  (validates the partial-unique-index invariant by
 *                          flipping any pre-existing ACTIVE contract to RENEWED)
 *   renew()     → atomic close (RENEWED) + new ACTIVE contract
 *   terminate() → TERMINATED (typically called from TerminationService)
 *   expire()    → EXPIRED (called by a scheduled job when end_date passes —
 *                          out of scope for M64, lands in M68)
 * </pre>
 *
 * <p>Audit and access-scope follow the established pattern. Workflow approval
 * for contract sign-off is intentionally out of scope here — that's
 * {@code CONTRACT_CHANGE_APPROVAL}'s job for amendments. Initial sign-off is
 * captured by the {@code signedByEmployeeAt} / {@code signedByHrAt} timestamps.
 */
@Service
public class EmploymentContractService {

    private static final String MODULE = "LIFECYCLE";
    private static final String ENTITY = "EmploymentContract";

    private final EmploymentContractRepository repository;
    private final EmployeeRepository employees;
    private final AuditService audit;
    private final AccessScopeService accessScope;
    private final CurrentRequest currentRequest;

    public EmploymentContractService(EmploymentContractRepository repository,
                                      EmployeeRepository employees,
                                      AuditService audit,
                                      AccessScopeService accessScope,
                                      CurrentRequest currentRequest) {
        this.repository = repository;
        this.employees = employees;
        this.audit = audit;
        this.accessScope = accessScope;
        this.currentRequest = currentRequest;
    }

    // ── Reads ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ContractResponse> listFor(UUID employeeId) {
        ensureEmployeeAccessible(employeeId);
        return repository.findByEmployeeIdOrderByStartDateDesc(employeeId)
                .stream().map(ContractResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ContractResponse get(UUID id) {
        EmploymentContract c = loadOrThrow(id);
        ensureEmployeeAccessible(c.getEmployeeId());
        return ContractResponse.from(c);
    }

    @Transactional(readOnly = true)
    public ContractResponse currentFor(UUID employeeId) {
        ensureEmployeeAccessible(employeeId);
        return repository.findByEmployeeIdAndStatus(employeeId, ContractStatus.ACTIVE)
                .map(ContractResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No active contract for employee " + employeeId));
    }

    // ── Writes ────────────────────────────────────────────────────────────────

    @Transactional
    public ContractResponse create(UUID employeeId, ContractRequest req) {
        Employee emp = employees.findById(employeeId).orElseThrow(() ->
                new BadRequestException("Employee not found: " + employeeId));
        validateDates(req);

        EmploymentContract c = new EmploymentContract();
        c.setContractNo(nextContractNo());
        c.setEmployeeId(emp.getId());
        apply(c, req);
        c.setStatus(ContractStatus.DRAFT);
        c.setCreatedBy(currentRequest.username());
        c.setUpdatedBy(currentRequest.username());
        EmploymentContract saved = repository.save(c);

        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, ContractResponse.from(saved));
        return ContractResponse.from(saved);
    }

    @Transactional
    public ContractResponse update(UUID id, ContractRequest req) {
        EmploymentContract c = loadOrThrow(id);
        if (c.getStatus() != ContractStatus.DRAFT) {
            throw new BadRequestException(
                    "Only DRAFT contracts can be edited — use renew() or terminate() instead");
        }
        validateDates(req);
        ContractResponse before = ContractResponse.from(c);

        apply(c, req);
        c.setUpdatedBy(currentRequest.username());
        EmploymentContract saved = repository.save(c);

        audit.record(MODULE, ENTITY, id.toString(),
                "UPDATE", before, ContractResponse.from(saved));
        return ContractResponse.from(saved);
    }

    /**
     * Promote a DRAFT contract to ACTIVE. If the employee already has an ACTIVE
     * contract this is treated as a <strong>renewal</strong>: the prior row
     * flips to RENEWED in the same transaction so the
     * {@code uq_contract_one_active_per_employee} partial unique index never trips.
     */
    @Transactional
    public ContractResponse activate(UUID id) {
        EmploymentContract incoming = loadOrThrow(id);
        if (incoming.getStatus() != ContractStatus.DRAFT) {
            throw new BadRequestException(
                    "Only DRAFT contracts can be activated — current status: " + incoming.getStatus());
        }
        ContractResponse before = ContractResponse.from(incoming);

        // Atomic renewal: close any pre-existing ACTIVE contract first.
        repository.findByEmployeeIdAndStatus(incoming.getEmployeeId(), ContractStatus.ACTIVE)
                .ifPresent(prior -> {
                    prior.setStatus(ContractStatus.RENEWED);
                    prior.setUpdatedBy(currentRequest.username());
                    // Cap the prior contract on the day before the new one starts so
                    // the timeline is contiguous and gap-free.
                    if (prior.getEndDate() == null
                            || prior.getEndDate().isAfter(incoming.getStartDate().minusDays(1))) {
                        prior.setEndDate(incoming.getStartDate().minusDays(1));
                    }
                    repository.save(prior);
                    audit.record(MODULE, ENTITY, prior.getId().toString(),
                            "RENEWED", null,
                            java.util.Map.of("supersededBy", incoming.getId().toString()));
                });
        repository.flush(); // ensure prior is RENEWED before we INSERT another ACTIVE row

        incoming.setStatus(ContractStatus.ACTIVE);
        incoming.setUpdatedBy(currentRequest.username());
        EmploymentContract saved = repository.save(incoming);

        audit.record(MODULE, ENTITY, id.toString(),
                "ACTIVATED", before, ContractResponse.from(saved));
        return ContractResponse.from(saved);
    }

    @Transactional
    public ContractResponse signByEmployee(UUID id) {
        return sign(id, /*byEmployee*/ true);
    }

    @Transactional
    public ContractResponse signByHr(UUID id) {
        return sign(id, /*byEmployee*/ false);
    }

    private ContractResponse sign(UUID id, boolean byEmployee) {
        EmploymentContract c = loadOrThrow(id);
        if (c.getStatus() != ContractStatus.DRAFT && c.getStatus() != ContractStatus.ACTIVE) {
            throw new BadRequestException(
                    "Cannot sign a contract in status " + c.getStatus());
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (byEmployee) c.setSignedByEmployeeAt(now);
        else            c.setSignedByHrAt(now);
        c.setUpdatedBy(currentRequest.username());
        EmploymentContract saved = repository.save(c);
        audit.record(MODULE, ENTITY, id.toString(),
                byEmployee ? "SIGN_EMPLOYEE" : "SIGN_HR",
                null, java.util.Map.of("signedAt", now.toString()));
        return ContractResponse.from(saved);
    }

    @Transactional
    public ContractResponse terminate(UUID id, String reason) {
        EmploymentContract c = loadOrThrow(id);
        if (c.getStatus() != ContractStatus.ACTIVE && c.getStatus() != ContractStatus.DRAFT) {
            throw new BadRequestException(
                    "Cannot terminate a contract in status " + c.getStatus());
        }
        ContractResponse before = ContractResponse.from(c);
        c.setStatus(ContractStatus.TERMINATED);
        c.setUpdatedBy(currentRequest.username());
        EmploymentContract saved = repository.save(c);
        audit.record(MODULE, ENTITY, id.toString(), "TERMINATED",
                before,
                java.util.Map.of("reason", reason == null ? "" : reason));
        return ContractResponse.from(saved);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void apply(EmploymentContract c, ContractRequest req) {
        c.setContractType(req.contractType());
        c.setStartDate(req.startDate());
        c.setEndDate(req.endDate());
        c.setProbationEndDate(req.probationEndDate());
        c.setNoticePeriodDays(req.noticePeriodDays() != null ? req.noticePeriodDays() : 30);
        c.setHasConfidentiality(Boolean.TRUE.equals(req.hasConfidentiality()));
        c.setNonCompeteEndDate(req.nonCompeteEndDate());
        c.setNotes(req.notes());
    }

    private void validateDates(ContractRequest req) {
        if (req.endDate() != null && req.endDate().isBefore(req.startDate())) {
            throw new BadRequestException("endDate cannot be before startDate");
        }
        if (req.probationEndDate() != null && req.probationEndDate().isBefore(req.startDate())) {
            throw new BadRequestException("probationEndDate cannot be before startDate");
        }
    }

    private EmploymentContract loadOrThrow(UUID id) {
        return repository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Contract not found: " + id));
    }

    private void ensureEmployeeAccessible(UUID employeeId) {
        if (!accessScope.isAccessible(employeeId)) {
            throw new ResourceNotFoundException("Employee not found: " + employeeId);
        }
    }

    private String nextContractNo() {
        return String.format("CT-%05d", repository.nextContractNoSequence());
    }
}
