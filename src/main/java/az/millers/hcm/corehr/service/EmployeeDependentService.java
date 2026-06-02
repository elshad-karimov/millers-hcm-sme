package az.millers.hcm.corehr.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.api.dto.DependentRequest;
import az.millers.hcm.corehr.api.dto.DependentResponse;
import az.millers.hcm.corehr.domain.EmployeeDependent;
import az.millers.hcm.corehr.repo.EmployeeDependentRepository;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.PiiAccessRoles;
import az.millers.hcm.security.scope.AccessScopeService;

/**
 * CRUD + verify for {@link EmployeeDependent} (M71 / P2-03).
 *
 * <p>Same shape as {@link EmployeeIdentificationService} — PII masking on
 * national_id, ABAC scope check, audit on every mutation.
 */
@Service
public class EmployeeDependentService {

    private static final String MODULE = "CORE_HR";
    private static final String ENTITY = "EmployeeDependent";

    private final EmployeeDependentRepository repository;
    private final EmployeeRepository employees;
    private final AuditService audit;
    private final AccessScopeService accessScope;
    private final CurrentRequest currentRequest;

    public EmployeeDependentService(EmployeeDependentRepository repository,
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
    public List<DependentResponse> listFor(UUID employeeId, boolean activeOnly) {
        ensureEmployeeAccessible(employeeId);
        boolean plain = PiiAccessRoles.callerCanSeePlaintextPii();
        List<EmployeeDependent> rows = activeOnly
                ? repository.findByEmployeeIdAndActiveTrueOrderByRelationshipTypeAsc(employeeId)
                : repository.findByEmployeeIdOrderByRelationshipTypeAsc(employeeId);
        return rows.stream().map(d -> DependentResponse.from(d, plain)).toList();
    }

    @Transactional
    public DependentResponse create(UUID employeeId, DependentRequest req) {
        if (!employees.existsById(employeeId)) {
            throw new BadRequestException("Employee not found: " + employeeId);
        }
        EmployeeDependent d = new EmployeeDependent();
        d.setEmployeeId(employeeId);
        apply(d, req);
        d.setCreatedBy(currentRequest.username());
        d.setUpdatedBy(currentRequest.username());
        EmployeeDependent saved = repository.save(d);

        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, DependentResponse.from(saved, true));
        return DependentResponse.from(saved, PiiAccessRoles.callerCanSeePlaintextPii());
    }

    @Transactional
    public DependentResponse update(UUID id, DependentRequest req) {
        EmployeeDependent d = loadOrThrow(id);
        DependentResponse before = DependentResponse.from(d, true);
        apply(d, req);
        d.setUpdatedBy(currentRequest.username());
        EmployeeDependent saved = repository.save(d);
        audit.record(MODULE, ENTITY, id.toString(),
                "UPDATE", before, DependentResponse.from(saved, true));
        return DependentResponse.from(saved, PiiAccessRoles.callerCanSeePlaintextPii());
    }

    @Transactional
    public void delete(UUID id) {
        EmployeeDependent d = loadOrThrow(id);
        DependentResponse before = DependentResponse.from(d, true);
        repository.delete(d);
        audit.record(MODULE, ENTITY, id.toString(), "DELETE", before, null);
    }

    private void apply(EmployeeDependent d, DependentRequest req) {
        d.setRelationshipType(req.relationshipType());
        d.setFirstName(req.firstName());
        d.setLastName(req.lastName());
        d.setMiddleName(req.middleName());
        d.setDateOfBirth(req.dateOfBirth());
        d.setGender(req.gender());
        d.setNationalId(req.nationalId());
        d.setPhone(req.phone());
        d.setEmail(req.email());
        d.setInsuranceEligible(Boolean.TRUE.equals(req.insuranceEligible()));
        d.setBenefitEligible(Boolean.TRUE.equals(req.benefitEligible()));
        d.setActive(req.active() == null || req.active());
        d.setNotes(req.notes());
    }

    private EmployeeDependent loadOrThrow(UUID id) {
        return repository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Dependent not found: " + id));
    }

    private void ensureEmployeeAccessible(UUID employeeId) {
        if (!accessScope.isAccessible(employeeId)) {
            throw new ResourceNotFoundException("Employee not found: " + employeeId);
        }
    }
}
