package az.millers.hcm.corehr.service;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.OnboardingWorkflow;
import az.millers.hcm.corehr.api.dto.EmployeeRequest;
import az.millers.hcm.corehr.api.dto.EmployeeResponse;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.domain.EmploymentStatus;
import az.millers.hcm.corehr.domain.EmploymentType;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.scope.AccessScopeService;

@Service
public class EmployeeService {

    private static final String MODULE = "CORE_HR";
    private static final String ENTITY = "Employee";

    private final EmployeeRepository repository;
    private final AuditService auditService;
    private final OnboardingWorkflow onboardingWorkflow;
    private final CurrentRequest currentRequest;
    private final AccessScopeService accessScope;

    public EmployeeService(EmployeeRepository repository,
                           AuditService auditService,
                           OnboardingWorkflow onboardingWorkflow,
                           CurrentRequest currentRequest,
                           AccessScopeService accessScope) {
        this.repository = repository;
        this.auditService = auditService;
        this.onboardingWorkflow = onboardingWorkflow;
        this.currentRequest = currentRequest;
        this.accessScope = accessScope;
    }

    @Transactional(readOnly = true)
    public Page<Employee> list(String search, EmploymentStatus status, Pageable pageable) {
        // ABAC scope (PRD 14.9): HR/admin/auditor → unrestricted, others see
        // only their reporting chain (DEPARTMENT_MANAGER) or themselves (EMPLOYEE).
        Set<UUID> scope = accessScope.scopeOrNullForCurrentUser();
        if (scope == null) {
            if (StringUtils.hasText(search)) {
                return repository.findByLastNameContainingIgnoreCaseOrFirstNameContainingIgnoreCase(
                        search, search, pageable);
            }
            if (status != null) {
                return repository.findByEmploymentStatus(status, pageable);
            }
            return repository.findAll(pageable);
        }
        if (scope.isEmpty()) {
            return Page.empty(pageable);
        }
        if (StringUtils.hasText(search)) {
            return repository.findByIdInAndNameContaining(scope, search, pageable);
        }
        if (status != null) {
            return repository.findByIdInAndEmploymentStatus(scope, status, pageable);
        }
        return repository.findByIdIn(scope, pageable);
    }

    @Transactional(readOnly = true)
    public Employee get(UUID id) {
        Employee e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + id));
        // ABAC: a scoped caller (DEPARTMENT_MANAGER / EMPLOYEE) sees only
        // employees in their accessible set. Surface as 404 — not 403 — so
        // the response doesn't leak the row's existence.
        if (!accessScope.isAccessible(e.getId())) {
            throw new ResourceNotFoundException("Employee not found: " + id);
        }
        return e;
    }

    /**
     * Creates a new hire. The employee starts {@code ON_PROBATION}, the onboarding
     * workflow fires, and an audit entry is written (PRD 8.1.4 acceptance criterion).
     */
    @Transactional
    public Employee create(EmployeeRequest request) {
        validateManager(request.managerId());
        validateDelegation(null, request);
        if (StringUtils.hasText(request.email())
                && repository.existsByEmailIgnoreCase(request.email())) {
            throw new BadRequestException("An employee with this email already exists");
        }
        validateNationalIdUnique(request.nationalId(), null);

        Employee employee = new Employee();
        employee.setEmployeeNo(nextEmployeeNo());
        applyRequest(employee, request);
        employee.setEmploymentStatus(EmploymentStatus.ON_PROBATION);
        employee.setCreatedBy(currentRequest.username());
        employee.setUpdatedBy(currentRequest.username());

        Employee saved = repository.save(employee);

        onboardingWorkflow.start(saved);
        auditService.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, EmployeeResponse.from(saved));
        return saved;
    }

    @Transactional
    public Employee update(UUID id, EmployeeRequest request) {
        Employee employee = get(id);
        validateManager(request.managerId());
        if (request.managerId() != null && request.managerId().equals(id)) {
            throw new BadRequestException("An employee cannot be their own manager");
        }
        validateDelegation(id, request);
        validateNationalIdUnique(request.nationalId(), id);
        EmployeeResponse before = EmployeeResponse.from(employee);

        applyRequest(employee, request);
        employee.setUpdatedBy(currentRequest.username());
        Employee saved = repository.save(employee);

        auditService.record(MODULE, ENTITY, id.toString(),
                "UPDATE", before, EmployeeResponse.from(saved));
        return saved;
    }

    @Transactional
    public Employee changeStatus(UUID id, EmploymentStatus newStatus, String reason) {
        Employee employee = get(id);
        EmploymentStatus oldStatus = employee.getEmploymentStatus();
        if (oldStatus == newStatus) {
            throw new BadRequestException("Employee is already in status " + newStatus);
        }
        employee.setEmploymentStatus(newStatus);
        employee.setUpdatedBy(currentRequest.username());
        Employee saved = repository.save(employee);

        auditService.record(MODULE, ENTITY, id.toString(), "STATUS_CHANGE",
                new StatusSnapshot(oldStatus, null),
                new StatusSnapshot(newStatus, reason));
        return saved;
    }

    private void applyRequest(Employee employee, EmployeeRequest request) {
        employee.setFirstName(request.firstName());
        employee.setLastName(request.lastName());
        employee.setMiddleName(request.middleName());
        employee.setBirthDate(request.birthDate());
        employee.setGender(request.gender());
        employee.setMaritalStatus(request.maritalStatus());
        employee.setNationality(request.nationality());
        employee.setNationalId(request.nationalId());
        employee.setEmail(request.email());
        employee.setPhone(request.phone());
        employee.setHireDate(request.hireDate());
        employee.setDepartmentName(request.departmentName());
        employee.setPositionTitle(request.positionTitle());
        employee.setCostCentre(request.costCentre());
        employee.setOrgUnitId(request.orgUnitId());
        employee.setPositionId(request.positionId());
        employee.setManagerId(request.managerId());
        employee.setDelegateManagerId(request.delegateManagerId());
        employee.setDelegateFrom(request.delegateFrom());
        employee.setDelegateTo(request.delegateTo());
        // M61 / P1-09: employment type + FTE. Both default to a full-time
        // PERMANENT employee when the caller doesn't supply them, preserving
        // pre-M61 API behaviour.
        employee.setEmploymentType(
                request.employmentType() != null ? request.employmentType() : EmploymentType.PERMANENT);
        employee.setFtePercent(
                request.ftePercent() != null ? request.ftePercent() : new BigDecimal("100.00"));
    }

    private void validateManager(UUID managerId) {
        if (managerId != null && !repository.existsById(managerId)) {
            throw new BadRequestException("Manager not found: " + managerId);
        }
    }

    /**
     * Application-side uniqueness check on the AES-encrypted {@code national_id}
     * column (M61 / P1-01).
     *
     * <p>A DB UNIQUE constraint can't catch duplicates because each row is
     * encrypted with its own AES-GCM IV — identical plaintexts produce
     * different ciphertexts. We scan every populated row, decrypt via the
     * standard JPA AttributeConverter, and reject duplicates application-side.
     *
     * @param incoming  the candidate national ID — null / blank values short-circuit
     * @param selfId    the employee being updated (null on create) so we don't
     *                  reject the row's own existing value
     */
    private void validateNationalIdUnique(String incoming, UUID selfId) {
        if (!StringUtils.hasText(incoming)) return;
        String trimmed = incoming.trim();
        for (Object[] row : repository.findIdAndNationalIdWhereNationalIdNotNull()) {
            UUID rowId = (UUID) row[0];
            String stored = (String) row[1];
            if (selfId != null && selfId.equals(rowId)) continue;
            if (stored != null && stored.equalsIgnoreCase(trimmed)) {
                throw new BadRequestException(
                        "An employee with this national ID already exists");
            }
        }
    }

    /**
     * Defense-in-depth on top of the V33 check constraints. Yields a
     * friendlier 400 than the raw constraint-violation surface that
     * Hibernate would otherwise bubble up.
     *
     * @param selfId  the employee's id when updating, or {@code null}
     *                on create (no self-id available yet)
     */
    private void validateDelegation(UUID selfId, EmployeeRequest request) {
        UUID delegate = request.delegateManagerId();
        java.time.LocalDate from = request.delegateFrom();
        java.time.LocalDate to = request.delegateTo();
        int setCount = (delegate != null ? 1 : 0)
                + (from != null ? 1 : 0)
                + (to != null ? 1 : 0);
        if (setCount != 0 && setCount != 3) {
            throw new BadRequestException(
                    "Delegation fields must be set together: provide all three of "
                            + "delegateManagerId, delegateFrom, delegateTo — or none");
        }
        if (delegate == null) return;
        if (selfId != null && delegate.equals(selfId)) {
            throw new BadRequestException(
                    "An employee cannot delegate to themselves");
        }
        if (!repository.existsById(delegate)) {
            throw new BadRequestException("Delegate not found: " + delegate);
        }
        if (from.isAfter(to)) {
            throw new BadRequestException(
                    "delegateFrom must be on or before delegateTo");
        }
    }

    private String nextEmployeeNo() {
        return String.format("EMP-%05d", repository.nextEmployeeNoSequence());
    }

    private record StatusSnapshot(EmploymentStatus status, String reason) {
    }
}
