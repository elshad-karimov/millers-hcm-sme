package az.millers.hcm.corehr.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.api.dto.CertificationRequest;
import az.millers.hcm.corehr.api.dto.CertificationResponse;
import az.millers.hcm.corehr.domain.EmployeeCertification;
import az.millers.hcm.corehr.domain.VerificationStatus;
import az.millers.hcm.corehr.repo.EmployeeCertificationRepository;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.PiiAccessRoles;
import az.millers.hcm.security.scope.AccessScopeService;

/**
 * CRUD + verify for {@link EmployeeCertification} (M65 / P1-13).
 *
 * <p>Same shape as {@link EmployeeIdentificationService} from M63 — verification
 * flow, PII masking on the license number, edit-clears-verification rule.
 */
@Service
public class EmployeeCertificationService {

    private static final String MODULE = "CORE_HR";
    private static final String ENTITY = "EmployeeCertification";

    private final EmployeeCertificationRepository repository;
    private final EmployeeRepository employees;
    private final AuditService audit;
    private final AccessScopeService accessScope;
    private final CurrentRequest currentRequest;

    public EmployeeCertificationService(EmployeeCertificationRepository repository,
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
    public List<CertificationResponse> listFor(UUID employeeId) {
        ensureEmployeeAccessible(employeeId);
        boolean plain = callerCanSeePlaintextNumber();
        return repository.findByEmployeeIdOrderByCertificationNameAsc(employeeId)
                .stream()
                .map(c -> CertificationResponse.from(c, plain))
                .toList();
    }

    @Transactional(readOnly = true)
    public CertificationResponse get(UUID id) {
        EmployeeCertification c = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Certification not found: " + id));
        ensureEmployeeAccessible(c.getEmployeeId());
        return CertificationResponse.from(c, callerCanSeePlaintextNumber());
    }

    @Transactional
    public CertificationResponse create(UUID employeeId, CertificationRequest req) {
        if (!employees.existsById(employeeId)) {
            throw new BadRequestException("Employee not found: " + employeeId);
        }
        validateDates(req);

        EmployeeCertification c = new EmployeeCertification();
        c.setEmployeeId(employeeId);
        apply(c, req);
        c.setVerificationStatus(VerificationStatus.UNVERIFIED);
        c.setCreatedBy(currentRequest.username());
        c.setUpdatedBy(currentRequest.username());
        EmployeeCertification saved = repository.save(c);

        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, CertificationResponse.from(saved, true));
        return CertificationResponse.from(saved, callerCanSeePlaintextNumber());
    }

    @Transactional
    public CertificationResponse update(UUID id, CertificationRequest req) {
        EmployeeCertification c = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Certification not found: " + id));
        validateDates(req);
        CertificationResponse before = CertificationResponse.from(c, true);

        apply(c, req);
        // Any edit invalidates the verification — HR must re-verify after renewal.
        c.setVerificationStatus(VerificationStatus.UNVERIFIED);
        c.setVerifiedBy(null);
        c.setVerifiedAt(null);
        c.setUpdatedBy(currentRequest.username());
        EmployeeCertification saved = repository.save(c);

        audit.record(MODULE, ENTITY, id.toString(),
                "UPDATE", before, CertificationResponse.from(saved, true));
        return CertificationResponse.from(saved, callerCanSeePlaintextNumber());
    }

    @Transactional
    public CertificationResponse verify(UUID id, VerificationStatus newStatus) {
        if (newStatus == VerificationStatus.UNVERIFIED) {
            throw new BadRequestException("Cannot manually set verification status back to UNVERIFIED");
        }
        EmployeeCertification c = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Certification not found: " + id));
        CertificationResponse before = CertificationResponse.from(c, true);

        c.setVerificationStatus(newStatus);
        c.setVerifiedBy(currentRequest.username());
        c.setVerifiedAt(java.time.OffsetDateTime.now());
        c.setUpdatedBy(currentRequest.username());
        EmployeeCertification saved = repository.save(c);

        audit.record(MODULE, ENTITY, id.toString(),
                "VERIFY_" + newStatus, before, CertificationResponse.from(saved, true));
        return CertificationResponse.from(saved, callerCanSeePlaintextNumber());
    }

    @Transactional
    public void delete(UUID id) {
        EmployeeCertification c = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Certification not found: " + id));
        CertificationResponse before = CertificationResponse.from(c, true);
        repository.delete(c);
        audit.record(MODULE, ENTITY, id.toString(), "DELETE", before, null);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void apply(EmployeeCertification c, CertificationRequest req) {
        c.setCertificationName(req.certificationName());
        c.setIssuingAuthority(req.issuingAuthority());
        c.setLicenseNumber(req.licenseNumber());
        c.setIssueDate(req.issueDate());
        c.setExpiryDate(req.expiryDate());
        c.setRequiredForPositionId(req.requiredForPositionId());
        c.setNotes(req.notes());
    }

    private void validateDates(CertificationRequest req) {
        if (req.issueDate() != null && req.expiryDate() != null
                && req.issueDate().isAfter(req.expiryDate())) {
            throw new BadRequestException("issueDate must be on or before expiryDate");
        }
    }

    private void ensureEmployeeAccessible(UUID employeeId) {
        if (!accessScope.isAccessible(employeeId)) {
            throw new ResourceNotFoundException("Employee not found: " + employeeId);
        }
    }

    /**
     * PII gate — delegates to the shared {@link PiiAccessRoles} helper.
     */
    private boolean callerCanSeePlaintextNumber() {
        return PiiAccessRoles.callerCanSeePlaintextPii();
    }
}
