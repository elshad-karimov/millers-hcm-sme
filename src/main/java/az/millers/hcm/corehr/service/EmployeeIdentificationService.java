package az.millers.hcm.corehr.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.api.dto.IdentificationRequest;
import az.millers.hcm.corehr.api.dto.IdentificationResponse;
import az.millers.hcm.corehr.domain.EmployeeIdentification;
import az.millers.hcm.corehr.domain.VerificationStatus;
import az.millers.hcm.corehr.repo.EmployeeIdentificationRepository;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.PiiAccessRoles;
import az.millers.hcm.security.scope.AccessScopeService;

/**
 * CRUD + verify operations for {@link EmployeeIdentification} (M63 / P1-04).
 *
 * <p>Reads: all roles that can already access the parent employee
 * (HR_ADMIN, HR_SPECIALIST, DEPARTMENT_MANAGER scoped to their reports,
 * SYSTEM_ADMIN, AUDITOR). Plain document number is only returned to
 * SYSTEM_ADMIN / HR_ADMIN / AUDITOR — every other role sees the
 * last-4-chars mask returned by {@link IdentificationResponse}.
 *
 * <p>Writes: HR_ADMIN / SYSTEM_ADMIN only. Enforced by {@code @PreAuthorize}
 * on the controller; this service stays role-agnostic.
 */
@Service
public class EmployeeIdentificationService {

    private static final String MODULE = "CORE_HR";
    private static final String ENTITY = "EmployeeIdentification";

    private final EmployeeIdentificationRepository repository;
    private final EmployeeRepository employees;
    private final AuditService audit;
    private final AccessScopeService accessScope;
    private final CurrentRequest currentRequest;

    public EmployeeIdentificationService(EmployeeIdentificationRepository repository,
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

    @Transactional(readOnly = true)
    public List<IdentificationResponse> listFor(UUID employeeId) {
        ensureEmployeeAccessible(employeeId);
        boolean plain = callerCanSeePlaintextNumber();
        return repository.findByEmployeeIdOrderByDocumentTypeAscIssueDateDesc(employeeId)
                .stream()
                .map(e -> IdentificationResponse.from(e, plain))
                .toList();
    }

    @Transactional(readOnly = true)
    public IdentificationResponse get(UUID id) {
        EmployeeIdentification e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Identification not found: " + id));
        ensureEmployeeAccessible(e.getEmployeeId());
        return IdentificationResponse.from(e, callerCanSeePlaintextNumber());
    }

    @Transactional
    public IdentificationResponse create(UUID employeeId, IdentificationRequest req) {
        if (!employees.existsById(employeeId)) {
            throw new BadRequestException("Employee not found: " + employeeId);
        }
        validateDateOrder(req);

        EmployeeIdentification e = new EmployeeIdentification();
        e.setEmployeeId(employeeId);
        apply(e, req);
        e.setVerificationStatus(VerificationStatus.UNVERIFIED);
        e.setCreatedBy(currentRequest.username());
        e.setUpdatedBy(currentRequest.username());
        EmployeeIdentification saved = repository.save(e);

        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, IdentificationResponse.from(saved, true));
        return IdentificationResponse.from(saved, callerCanSeePlaintextNumber());
    }

    @Transactional
    public IdentificationResponse update(UUID id, IdentificationRequest req) {
        EmployeeIdentification e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Identification not found: " + id));
        validateDateOrder(req);
        IdentificationResponse before = IdentificationResponse.from(e, true);

        apply(e, req);
        // Editing document content invalidates any prior verification — HR
        // must re-verify after a renewal.
        e.setVerificationStatus(VerificationStatus.UNVERIFIED);
        e.setVerifiedBy(null);
        e.setVerifiedAt(null);
        e.setUpdatedBy(currentRequest.username());
        EmployeeIdentification saved = repository.save(e);

        audit.record(MODULE, ENTITY, id.toString(),
                "UPDATE", before, IdentificationResponse.from(saved, true));
        return IdentificationResponse.from(saved, callerCanSeePlaintextNumber());
    }

    @Transactional
    public IdentificationResponse verify(UUID id, VerificationStatus newStatus) {
        if (newStatus == VerificationStatus.UNVERIFIED) {
            throw new BadRequestException("Cannot manually set verification status back to UNVERIFIED");
        }
        EmployeeIdentification e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Identification not found: " + id));
        IdentificationResponse before = IdentificationResponse.from(e, true);

        e.setVerificationStatus(newStatus);
        e.setVerifiedBy(currentRequest.username());
        e.setVerifiedAt(java.time.OffsetDateTime.now());
        e.setUpdatedBy(currentRequest.username());
        EmployeeIdentification saved = repository.save(e);

        audit.record(MODULE, ENTITY, id.toString(),
                "VERIFY_" + newStatus, before, IdentificationResponse.from(saved, true));
        return IdentificationResponse.from(saved, callerCanSeePlaintextNumber());
    }

    @Transactional
    public void delete(UUID id) {
        EmployeeIdentification e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Identification not found: " + id));
        IdentificationResponse before = IdentificationResponse.from(e, true);
        repository.delete(e);
        audit.record(MODULE, ENTITY, id.toString(), "DELETE", before, null);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void apply(EmployeeIdentification e, IdentificationRequest req) {
        e.setDocumentType(req.documentType());
        e.setDocumentNumber(req.documentNumber().trim());
        e.setIssueDate(req.issueDate());
        e.setExpiryDate(req.expiryDate());
        e.setIssuingAuthority(req.issuingAuthority());
        e.setIssuingCountry(req.issuingCountry());
        e.setNotes(req.notes());
    }

    private void validateDateOrder(IdentificationRequest req) {
        if (req.issueDate() != null && req.expiryDate() != null
                && req.issueDate().isAfter(req.expiryDate())) {
            throw new BadRequestException("issueDate must be on or before expiryDate");
        }
    }

    private void ensureEmployeeAccessible(UUID employeeId) {
        if (!accessScope.isAccessible(employeeId)) {
            // Surface as 404 rather than 403 to avoid leaking row existence
            // (PRD §14.9 — consistent with the rest of the codebase).
            throw new ResourceNotFoundException("Employee not found: " + employeeId);
        }
    }

    /**
     * PII gate — delegates to {@link PiiAccessRoles#callerCanSeePlaintextPii()}.
     * Kept as a one-liner so the call sites read naturally; the centralised
     * role list is the source of truth.
     */
    private boolean callerCanSeePlaintextNumber() {
        return PiiAccessRoles.callerCanSeePlaintextPii();
    }
}
