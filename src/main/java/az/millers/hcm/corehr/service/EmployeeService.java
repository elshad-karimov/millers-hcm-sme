package az.millers.hcm.corehr.service;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.OnboardingWorkflow;
import az.millers.hcm.corehr.api.dto.EmployeeRequest;
import az.millers.hcm.corehr.api.dto.EmployeeResponse;
import az.millers.hcm.corehr.api.dto.EmployeeSearchFilter;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.domain.EmploymentStatus;
import az.millers.hcm.corehr.domain.EmploymentType;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.scope.AccessScopeService;
import az.millers.hcm.staffing.service.PositionHeadcountService;
import az.millers.hcm.staffing.service.StaffingService;
import az.millers.hcm.common.BusinessNumbers;

@Service
public class EmployeeService {

    private static final String MODULE = "CORE_HR";
    private static final String ENTITY = "Employee";

    private final EmployeeRepository repository;
    private final AuditService auditService;
    private final OnboardingWorkflow onboardingWorkflow;
    private final CurrentRequest currentRequest;
    private final AccessScopeService accessScope;
    private final EmployeeHistoryService historyService;
    private final PositionHeadcountService headcountGate;
    private final StaffingService staffingService;
    // M249 — Phase D.2: every position assignment open/close is mirrored
    // into the position_occupancy table for full history + auto-grant.
    private final az.millers.hcm.staffing.service.PositionOccupancyService occupancyService;

    public EmployeeService(EmployeeRepository repository,
                           AuditService auditService,
                           OnboardingWorkflow onboardingWorkflow,
                           CurrentRequest currentRequest,
                           AccessScopeService accessScope,
                           EmployeeHistoryService historyService,
                           PositionHeadcountService headcountGate,
                           StaffingService staffingService,
                           az.millers.hcm.staffing.service.PositionOccupancyService occupancyService) {
        this.repository = repository;
        this.auditService = auditService;
        this.onboardingWorkflow = onboardingWorkflow;
        this.currentRequest = currentRequest;
        this.accessScope = accessScope;
        this.historyService = historyService;
        this.headcountGate = headcountGate;
        this.staffingService = staffingService;
        this.occupancyService = occupancyService;
    }

    /**
     * Pre-M69 list signature, preserved for the controller's
     * legacy {@code search} + {@code status} params. Delegates to the new
     * {@link #list(EmployeeSearchFilter, Pageable)} so the ABAC scope + Spec
     * composition lives in exactly one place.
     */
    @Transactional(readOnly = true)
    public Page<Employee> list(String search, EmploymentStatus status, Pageable pageable) {
        EmployeeSearchFilter filter = new EmployeeSearchFilter(
                StringUtils.hasText(search) ? search : null,
                status == null ? null : java.util.Set.of(status),
                null, null, null, null, null, null, null, null, null, null, null);
        return list(filter, pageable);
    }

    /**
     * M69 / P1-15 — advanced search. Builds a {@link Specification} from the
     * filter, then AND-combines it with the ABAC scope predicate. Both
     * predicates pass through the JpaSpecificationExecutor so Postgres sees
     * one query with one WHERE clause — no N+1, no in-memory filtering.
     */
    @Transactional(readOnly = true)
    public Page<Employee> list(EmployeeSearchFilter filter, Pageable pageable) {
        Specification<Employee> userFilter = EmployeeSpecifications.from(filter);

        // ABAC scope (PRD 14.9): unrestricted callers see everything, scoped
        // callers (DEPARTMENT_MANAGER / EMPLOYEE / org-unit HR_SPECIALIST) get
        // their employee-id set anded in.
        Set<UUID> scope = accessScope.scopeOrNullForCurrentUser();
        Specification<Employee> scopeFilter = scope == null
                ? EmployeeSpecifications.matchAll()
                : (scope.isEmpty()
                    ? (root, q, cb) -> cb.disjunction()      // empty scope → no rows
                    : EmployeeSpecifications.inScope(scope));

        return repository.findAll(userFilter.and(scopeFilter), pageable);
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
        // M109 — direct hire path must respect position budget.
        headcountGate.assertCanFill(request.positionId());

        Employee employee = new Employee();
        employee.setEmployeeNo(nextEmployeeNo());
        applyRequest(employee, request);
        employee.setEmploymentStatus(EmploymentStatus.ON_PROBATION);
        employee.setCreatedBy(currentRequest.username());
        employee.setUpdatedBy(currentRequest.username());

        Employee saved = repository.save(employee);

        // M109 — bump the seat counter so Position.occupiedHeadcount stays in
        // lockstep with the ground-truth employee table. Skipped when the new
        // hire has no position assignment yet.
        if (saved.getPositionId() != null) {
            staffingService.adjustOccupancy(saved.getPositionId(), +1,
                    "Direct hire " + saved.getEmployeeNo());
            // M249 — Phase D.2: mirror the assignment into position_occupancy
            // so the seat has a full history + the M250 grant list lands
            // in HR's PENDING queue automatically.
            occupancyService.openPrimary(saved.getId(), saved.getPositionId(),
                    saved.getHireDate(), "Direct hire " + saved.getEmployeeNo());
        }

        // M62 / P1-10 + P1-11: open the initial history slices at hire date.
        // This guarantees every employee has a non-empty history from day one
        // — downstream queries never need to special-case "no history yet".
        historyService.recordEmploymentSlice(saved, saved.getHireDate(),
                "Hired", MODULE, ENTITY, saved.getId().toString());
        historyService.recordStatusSlice(saved.getId(), saved.getEmploymentStatus(),
                saved.getHireDate(), "Hired", MODULE, ENTITY, saved.getId().toString());

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

        // M109 — if the position changes, gate the move AND keep seat counters
        // in sync. Same-position updates are no-ops at the gate.
        UUID oldPositionId = employee.getPositionId();
        UUID newPositionId = request.positionId();
        boolean positionChanged = !java.util.Objects.equals(oldPositionId, newPositionId);
        if (positionChanged) {
            headcountGate.assertCanMove(oldPositionId, newPositionId);
        }

        applyRequest(employee, request);
        employee.setUpdatedBy(currentRequest.username());
        Employee saved = repository.save(employee);

        if (positionChanged) {
            if (oldPositionId != null) {
                staffingService.adjustOccupancy(oldPositionId, -1,
                        "Position swap (out) for " + saved.getEmployeeNo());
                // M249 — close the active PRIMARY occupancy on the old seat.
                occupancyService.closeActivePrimary(saved.getId(), oldPositionId,
                        java.time.LocalDate.now(),
                        "Position swap to new seat for " + saved.getEmployeeNo());
            }
            if (newPositionId != null) {
                staffingService.adjustOccupancy(newPositionId, +1,
                        "Position swap (in) for " + saved.getEmployeeNo());
                // M249 — open a new PRIMARY occupancy on the new seat.
                // Auto-grants the new position's mandatory profile items
                // into HR's PENDING queue (M250).
                occupancyService.openPrimary(saved.getId(), newPositionId,
                        java.time.LocalDate.now(),
                        "Position swap from previous seat for " + saved.getEmployeeNo());
            }
        }

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

        // M62 / P1-11: status_history is the queryable source-of-truth.
        // Effective-dated as of today() — admin status transitions are
        // immediate. (Termination uses TerminationService which writes a
        // dedicated slice with the formal effective_date.)
        historyService.recordStatusSlice(saved.getId(), newStatus,
                java.time.LocalDate.now(), reason, MODULE, ENTITY, saved.getId().toString());

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
        employee.setTaxId(request.taxId());
        employee.setSocialInsuranceId(request.socialInsuranceId());
        employee.setEmail(request.email());
        employee.setPhone(request.phone());
        employee.setHireDate(request.hireDate());
        employee.setDepartmentName(request.departmentName());
        employee.setPositionTitle(request.positionTitle());
        employee.setCostCentre(request.costCentre());
        employee.setOrgUnitId(request.orgUnitId());
        employee.setPositionId(request.positionId());
        employee.setManagerId(request.managerId());
        employee.setWorkLocationId(request.workLocationId());
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
        // M66 / P1-08. Null = "use the default group" — no fallback assignment
        // needed here; LeaveAccrualService resolves it lazily.
        employee.setLeaveGroupId(request.leaveGroupId());
        // M75 / P2-19 + P2-21
        employee.setPayrollGroupId(request.payrollGroupId());
        UUID matrixMgr = request.matrixManagerId();
        if (matrixMgr != null && matrixMgr.equals(employee.getId())) {
            throw new BadRequestException("An employee cannot be their own matrix manager");
        }
        employee.setMatrixManagerId(matrixMgr);
        // M146 / §9 — functional manager (informational, no workflow effect).
        UUID funcMgr = request.functionalManagerId();
        if (funcMgr != null && funcMgr.equals(employee.getId())) {
            throw new BadRequestException("An employee cannot be their own functional manager");
        }
        employee.setFunctionalManagerId(funcMgr);
        // M78 / P2-15 — keep the existing value on update when the caller
        // doesn't supply one (Boolean object so null = "don't touch").
        if (request.rehireEligible() != null) {
            employee.setRehireEligible(request.rehireEligible());
        }
        // M132 — Section 1 cosmetic fields. All five are nullable;
        // passing null clears the field (consistent with how
        // middleName / nationality / etc. behave).
        employee.setPreferredName(request.preferredName());
        employee.setPlaceOfBirth(request.placeOfBirth());
        employee.setBloodGroup(request.bloodGroup());
        employee.setReligion(request.religion());
        employee.setNativeLanguage(request.nativeLanguage());
        // M133 — Section 3 contact fields. Same null-means-clear pattern.
        employee.setAltPhone(request.altPhone());
        employee.setWorkEmail(request.workEmail());
        employee.setWorkPhone(request.workPhone());
        employee.setExtension(request.extension());
        employee.setDeskNumber(request.deskNumber());
        // M134 — Section 4 employment fields. Seniority date is bounded
        // by the DB CHECK (cannot be in the future); reject early so
        // the caller gets a clean BadRequestException rather than a
        // generic constraint violation.
        if (request.seniorityDate() != null
                && request.seniorityDate().isAfter(java.time.LocalDate.now())) {
            throw new BadRequestException("seniorityDate cannot be in the future");
        }
        employee.setEmployeeCategory(request.employeeCategory());
        employee.setSeniorityDate(request.seniorityDate());
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
        return BusinessNumbers.format("EMP", 5, repository.nextEmployeeNoSequence());
    }

    private record StatusSnapshot(EmploymentStatus status, String reason) {
    }
}
