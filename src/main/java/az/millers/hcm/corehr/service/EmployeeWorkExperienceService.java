package az.millers.hcm.corehr.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.api.dto.WorkExperienceRequest;
import az.millers.hcm.corehr.api.dto.WorkExperienceResponse;
import az.millers.hcm.corehr.domain.EmployeeWorkExperience;
import az.millers.hcm.corehr.domain.VerificationStatus;
import az.millers.hcm.corehr.domain.WorkExperienceType;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.corehr.repo.EmployeeWorkExperienceRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.PiiAccessRoles;
import az.millers.hcm.security.scope.AccessScopeService;

/**
 * CRUD + verify for {@link EmployeeWorkExperience} (M71 / P2-05).
 *
 * <p>{@code lastSalary} is encrypted at rest — the response DTO masks it for
 * non-cleared roles via {@link PiiAccessRoles#callerCanSeePlaintextPii()}.
 */
@Service
public class EmployeeWorkExperienceService {

    private static final String MODULE = "CORE_HR";
    private static final String ENTITY = "EmployeeWorkExperience";

    private final EmployeeWorkExperienceRepository repository;
    private final EmployeeRepository employees;
    private final AuditService audit;
    private final AccessScopeService accessScope;
    private final CurrentRequest currentRequest;

    public EmployeeWorkExperienceService(EmployeeWorkExperienceRepository repository,
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
    public List<WorkExperienceResponse> listFor(UUID employeeId) {
        ensureEmployeeAccessible(employeeId);
        boolean canSeeSalary = PiiAccessRoles.callerCanSeePlaintextPii();
        return repository.findByEmployeeIdOrderByStartDateDesc(employeeId)
                .stream().map(e -> WorkExperienceResponse.from(e, canSeeSalary)).toList();
    }

    @Transactional
    public WorkExperienceResponse create(UUID employeeId, WorkExperienceRequest req) {
        if (!employees.existsById(employeeId)) {
            throw new BadRequestException("Employee not found: " + employeeId);
        }
        validateDates(req);
        EmployeeWorkExperience e = new EmployeeWorkExperience();
        e.setEmployeeId(employeeId);
        apply(e, req);
        e.setVerificationStatus(VerificationStatus.UNVERIFIED);
        e.setCreatedBy(currentRequest.username());
        e.setUpdatedBy(currentRequest.username());
        EmployeeWorkExperience saved = repository.save(e);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, WorkExperienceResponse.from(saved, true));
        return WorkExperienceResponse.from(saved, PiiAccessRoles.callerCanSeePlaintextPii());
    }

    @Transactional
    public WorkExperienceResponse update(UUID id, WorkExperienceRequest req) {
        EmployeeWorkExperience e = loadOrThrow(id);
        validateDates(req);
        WorkExperienceResponse before = WorkExperienceResponse.from(e, true);
        apply(e, req);
        e.setVerificationStatus(VerificationStatus.UNVERIFIED);
        e.setVerifiedBy(null);
        e.setVerifiedAt(null);
        e.setUpdatedBy(currentRequest.username());
        EmployeeWorkExperience saved = repository.save(e);
        audit.record(MODULE, ENTITY, id.toString(),
                "UPDATE", before, WorkExperienceResponse.from(saved, true));
        return WorkExperienceResponse.from(saved, PiiAccessRoles.callerCanSeePlaintextPii());
    }

    @Transactional
    public WorkExperienceResponse verify(UUID id, VerificationStatus newStatus) {
        if (newStatus == VerificationStatus.UNVERIFIED) {
            throw new BadRequestException("Cannot manually set verification status back to UNVERIFIED");
        }
        EmployeeWorkExperience e = loadOrThrow(id);
        WorkExperienceResponse before = WorkExperienceResponse.from(e, true);
        e.setVerificationStatus(newStatus);
        e.setVerifiedBy(currentRequest.username());
        e.setVerifiedAt(OffsetDateTime.now());
        e.setUpdatedBy(currentRequest.username());
        EmployeeWorkExperience saved = repository.save(e);
        audit.record(MODULE, ENTITY, id.toString(),
                "VERIFY_" + newStatus, before, WorkExperienceResponse.from(saved, true));
        return WorkExperienceResponse.from(saved, PiiAccessRoles.callerCanSeePlaintextPii());
    }

    @Transactional
    public void delete(UUID id) {
        EmployeeWorkExperience e = loadOrThrow(id);
        WorkExperienceResponse before = WorkExperienceResponse.from(e, true);
        repository.delete(e);
        audit.record(MODULE, ENTITY, id.toString(), "DELETE", before, null);
    }

    private void apply(EmployeeWorkExperience e, WorkExperienceRequest req) {
        e.setExperienceType(req.experienceType() != null
                ? req.experienceType() : WorkExperienceType.EXTERNAL);
        e.setEmployerName(req.employerName());
        e.setIndustry(req.industry());
        e.setJobTitle(req.jobTitle());
        e.setStartDate(req.startDate());
        e.setEndDate(req.endDate());
        e.setReasonForLeaving(req.reasonForLeaving());
        // Persist salary as plain decimal string — the JPA converter encrypts
        // it on write. Null stays null.
        e.setLastSalary(req.lastSalary() == null ? null : req.lastSalary().toPlainString());
        e.setLastSalaryCurrency(req.lastSalaryCurrency() == null
                ? null : req.lastSalaryCurrency().toUpperCase());
        e.setResponsibilities(req.responsibilities());
        e.setReferenceContact(req.referenceContact());
        e.setReferenceVerified(Boolean.TRUE.equals(req.referenceVerified()));
        e.setNotes(req.notes());
    }

    private void validateDates(WorkExperienceRequest req) {
        if (req.endDate() != null && req.endDate().isBefore(req.startDate())) {
            throw new BadRequestException("endDate cannot be before startDate");
        }
    }

    private EmployeeWorkExperience loadOrThrow(UUID id) {
        return repository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Work experience not found: " + id));
    }

    private void ensureEmployeeAccessible(UUID employeeId) {
        if (!accessScope.isAccessible(employeeId)) {
            throw new ResourceNotFoundException("Employee not found: " + employeeId);
        }
    }
}
