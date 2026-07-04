package az.millers.hcm.corehr.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.api.dto.EducationRequest;
import az.millers.hcm.corehr.api.dto.EducationResponse;
import az.millers.hcm.corehr.domain.EmployeeEducation;
import az.millers.hcm.corehr.domain.VerificationStatus;
import az.millers.hcm.corehr.repo.EmployeeEducationRepository;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.scope.AccessScopeService;

/**
 * CRUD + verify for {@link EmployeeEducation} (M71 / P2-04). Verification
 * flow mirrors {@link EmployeeIdentificationService} and
 * {@link EmployeeCertificationService}.
 */
@Service
public class EmployeeEducationService {

    private static final String MODULE = "CORE_HR";
    private static final String ENTITY = "EmployeeEducation";

    private final EmployeeEducationRepository repository;
    private final EmployeeRepository employees;
    private final AuditService audit;
    private final AccessScopeService accessScope;
    private final CurrentRequest currentRequest;

    public EmployeeEducationService(EmployeeEducationRepository repository,
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
    public List<EducationResponse> listFor(UUID employeeId) {
        ensureEmployeeAccessible(employeeId);
        return repository.findByEmployeeIdOrderByEndDateDescNullsFirst(employeeId)
                .stream().map(EducationResponse::from).toList();
    }

    @Transactional
    public EducationResponse create(UUID employeeId, EducationRequest req) {
        if (!employees.existsById(employeeId)) {
            throw new BadRequestException("Employee not found: " + employeeId);
        }
        validateDates(req);
        EmployeeEducation e = new EmployeeEducation();
        e.setEmployeeId(employeeId);
        apply(e, req);
        e.setVerificationStatus(VerificationStatus.UNVERIFIED);
        e.setCreatedBy(currentRequest.username());
        e.setUpdatedBy(currentRequest.username());
        EmployeeEducation saved = repository.save(e);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, EducationResponse.from(saved));
        return EducationResponse.from(saved);
    }

    @Transactional
    public EducationResponse update(UUID id, EducationRequest req) {
        EmployeeEducation e = loadOrThrow(id);
        validateDates(req);
        EducationResponse before = EducationResponse.from(e);
        apply(e, req);
        // Edit invalidates verification, same convention as M65 certifications.
        e.setVerificationStatus(VerificationStatus.UNVERIFIED);
        e.setVerifiedBy(null);
        e.setVerifiedAt(null);
        e.setUpdatedBy(currentRequest.username());
        EmployeeEducation saved = repository.save(e);
        audit.record(MODULE, ENTITY, id.toString(),
                "UPDATE", before, EducationResponse.from(saved));
        return EducationResponse.from(saved);
    }

    @Transactional
    public EducationResponse verify(UUID id, VerificationStatus newStatus) {
        if (newStatus == VerificationStatus.UNVERIFIED) {
            throw new BadRequestException("Cannot manually set verification status back to UNVERIFIED");
        }
        EmployeeEducation e = loadOrThrow(id);
        EducationResponse before = EducationResponse.from(e);
        e.setVerificationStatus(newStatus);
        e.setVerifiedBy(currentRequest.username());
        e.setVerifiedAt(OffsetDateTime.now());
        e.setUpdatedBy(currentRequest.username());
        EmployeeEducation saved = repository.save(e);
        audit.record(MODULE, ENTITY, id.toString(),
                "VERIFY_" + newStatus, before, EducationResponse.from(saved));
        return EducationResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        EmployeeEducation e = loadOrThrow(id);
        EducationResponse before = EducationResponse.from(e);
        repository.delete(e);
        audit.record(MODULE, ENTITY, id.toString(), "DELETE", before, null);
    }

    private void apply(EmployeeEducation e, EducationRequest req) {
        e.setEducationLevel(req.educationLevel());
        e.setInstitutionName(req.institutionName());
        e.setCountry(req.country());
        e.setDegree(req.degree());
        e.setMajor(req.major());
        e.setStartDate(req.startDate());
        e.setEndDate(req.endDate());
        e.setGpa(req.gpa());
        e.setNotes(req.notes());
    }

    private void validateDates(EducationRequest req) {
        if (req.startDate() != null && req.endDate() != null
                && req.startDate().isAfter(req.endDate())) {
            throw new BadRequestException("startDate must be on or before endDate");
        }
    }

    private EmployeeEducation loadOrThrow(UUID id) {
        return repository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Education record not found: " + id));
    }

    private void ensureEmployeeAccessible(UUID employeeId) {
        if (!accessScope.isAccessible(employeeId)) {
            throw new ResourceNotFoundException("Employee not found: " + employeeId);
        }
    }
}
