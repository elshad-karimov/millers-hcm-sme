package az.millers.hcm.corehr.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.api.dto.EmployeeAssignmentRequest;
import az.millers.hcm.corehr.api.dto.EmployeeAssignmentResponse;
import az.millers.hcm.corehr.domain.AssignmentType;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.domain.EmployeeAssignment;
import az.millers.hcm.corehr.repo.EmployeeAssignmentRepository;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.scope.AccessScopeService;
import az.millers.hcm.staffing.domain.Position;
import az.millers.hcm.staffing.repo.PositionRepository;
import az.millers.hcm.staffing.service.PositionHeadcountService;
import az.millers.hcm.staffing.service.StaffingService;

/**
 * CRUD for {@link EmployeeAssignment} — M75 / P2-20. Effective-dated via the
 * shared {@link az.millers.hcm.common.history.EffectiveDatedRecord} interface.
 *
 * <p>Invariants enforced at service layer (DB also has belt-and-braces):
 * <ul>
 *   <li>At most one open PRIMARY assignment per employee.</li>
 *   <li>Sum of {@code allocationPercent} across open assignments ≤ 100.</li>
 *   <li>Position must exist; employee must be accessible (scope-checked).</li>
 *   <li>Opening a new PRIMARY mirrors the change onto
 *       {@code Employee.positionId} so the legacy "current primary" shortcut
 *       stays in sync.</li>
 * </ul>
 */
@Service
public class EmployeeAssignmentService {

    private static final String MODULE = "CORE_HR";
    private static final String ENTITY = "EmployeeAssignment";
    private static final BigDecimal HUNDRED = new BigDecimal("100.00");

    private final EmployeeAssignmentRepository repository;
    private final EmployeeRepository employees;
    private final PositionRepository positions;
    private final AuditService audit;
    private final AccessScopeService accessScope;
    private final CurrentRequest currentRequest;
    private final PositionHeadcountService headcountGate;
    private final StaffingService staffingService;

    public EmployeeAssignmentService(EmployeeAssignmentRepository repository,
                                      EmployeeRepository employees,
                                      PositionRepository positions,
                                      AuditService audit,
                                      AccessScopeService accessScope,
                                      CurrentRequest currentRequest,
                                      PositionHeadcountService headcountGate,
                                      StaffingService staffingService) {
        this.repository = repository;
        this.employees = employees;
        this.positions = positions;
        this.audit = audit;
        this.accessScope = accessScope;
        this.currentRequest = currentRequest;
        this.headcountGate = headcountGate;
        this.staffingService = staffingService;
    }

    @Transactional(readOnly = true)
    public List<EmployeeAssignmentResponse> listFor(UUID employeeId) {
        ensureEmployeeAccessible(employeeId);
        return repository.findByEmployeeIdOrderByEffectiveFromDesc(employeeId)
                .stream().map(EmployeeAssignmentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<EmployeeAssignmentResponse> openFor(UUID employeeId) {
        ensureEmployeeAccessible(employeeId);
        return repository.findOpenForEmployee(employeeId)
                .stream().map(EmployeeAssignmentResponse::from).toList();
    }

    @Transactional
    public EmployeeAssignmentResponse create(EmployeeAssignmentRequest req) {
        Employee employee = employees.findById(req.employeeId())
                .orElseThrow(() -> new BadRequestException(
                        "Employee not found: " + req.employeeId()));
        ensureEmployeeAccessible(req.employeeId());
        Position position = positions.findById(req.positionId())
                .orElseThrow(() -> new BadRequestException(
                        "Position not found: " + req.positionId()));
        if (req.effectiveFrom() == null) {
            throw new BadRequestException("effectiveFrom is required");
        }
        if (req.assignmentType() == null) {
            throw new BadRequestException("assignmentType is required");
        }

        // PRIMARY: at most one open. Close the prior one (or supersede same-day).
        if (req.assignmentType() == AssignmentType.PRIMARY) {
            repository.findOpenPrimary(req.employeeId()).ifPresent(prior -> {
                if (prior.getEffectiveFrom().equals(req.effectiveFrom())) {
                    repository.delete(prior);
                } else {
                    prior.closeOn(req.effectiveFrom());
                    repository.save(prior);
                }
                // Load-bearing: uq_ea_one_open_primary allows a single open
                // PRIMARY row per employee, and Hibernate flushes inserts ahead
                // of updates and deletes. Without this the new open row is
                // INSERTed while the prior one is still open and Postgres
                // rejects it.
                repository.flush();
            });
        }

        BigDecimal allocation = normaliseAllocation(req.allocationPercent(), req.assignmentType());
        ensureAllocationCap(req.employeeId(), allocation, null);

        EmployeeAssignment a = new EmployeeAssignment();
        a.setEmployeeId(req.employeeId());
        a.setPositionId(req.positionId());
        a.setAssignmentType(req.assignmentType());
        a.setAllocationPercent(allocation);
        a.setMatrixManagerId(req.matrixManagerId());
        a.setEffectiveFrom(req.effectiveFrom());
        a.setEffectiveTo(req.effectiveTo());
        a.setNotes(req.notes());
        a.setCreatedBy(currentRequest.username());
        a.setUpdatedBy(currentRequest.username());
        EmployeeAssignment saved = repository.save(a);

        // Mirror PRIMARY → Employee.positionId so legacy queries keep working.
        // M109 — also gate the move and keep occupied counters in sync.
        if (saved.getAssignmentType() == AssignmentType.PRIMARY
                && saved.getEffectiveTo() == null) {
            UUID oldPositionId = employee.getPositionId();
            UUID newPositionId = position.getId();
            if (!java.util.Objects.equals(oldPositionId, newPositionId)) {
                headcountGate.assertCanMove(oldPositionId, newPositionId);
            }
            employee.setPositionId(newPositionId);
            employee.setUpdatedBy(currentRequest.username());
            employees.save(employee);
            if (!java.util.Objects.equals(oldPositionId, newPositionId)) {
                if (oldPositionId != null) {
                    staffingService.adjustOccupancy(oldPositionId, -1,
                            "PRIMARY assignment change (out) for " + employee.getEmployeeNo());
                }
                staffingService.adjustOccupancy(newPositionId, +1,
                        "PRIMARY assignment change (in) for " + employee.getEmployeeNo());
            }
        }

        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, EmployeeAssignmentResponse.from(saved));
        return EmployeeAssignmentResponse.from(saved);
    }

    @Transactional
    public EmployeeAssignmentResponse update(UUID id, EmployeeAssignmentRequest req) {
        EmployeeAssignment a = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Assignment not found: " + id));
        ensureEmployeeAccessible(a.getEmployeeId());

        if (!a.getEmployeeId().equals(req.employeeId())) {
            throw new BadRequestException("employeeId is immutable");
        }
        if (a.getAssignmentType() != req.assignmentType()) {
            throw new BadRequestException(
                    "assignmentType is immutable — close this row and create a new one");
        }
        if (!a.getEffectiveFrom().equals(req.effectiveFrom())) {
            throw new BadRequestException(
                    "effectiveFrom is immutable — supersede this row by creating a new one");
        }
        if (!positions.existsById(req.positionId())) {
            throw new BadRequestException("Position not found: " + req.positionId());
        }

        EmployeeAssignmentResponse before = EmployeeAssignmentResponse.from(a);

        BigDecimal allocation = normaliseAllocation(req.allocationPercent(), req.assignmentType());
        ensureAllocationCap(a.getEmployeeId(), allocation, a.getId());

        a.setPositionId(req.positionId());
        a.setAllocationPercent(allocation);
        a.setMatrixManagerId(req.matrixManagerId());
        a.setEffectiveTo(req.effectiveTo());
        a.setNotes(req.notes());
        a.setUpdatedBy(currentRequest.username());
        EmployeeAssignment saved = repository.save(a);

        audit.record(MODULE, ENTITY, id.toString(),
                "UPDATE", before, EmployeeAssignmentResponse.from(saved));
        return EmployeeAssignmentResponse.from(saved);
    }

    /**
     * Close (end-date) an open assignment. Sets {@code effective_to} directly
     * to the supplied date (defaulting to today) — distinct from the
     * supersede flow that uses {@link EmployeeAssignment#closeOn(LocalDate)}
     * to back-date by one day.
     */
    @Transactional
    public EmployeeAssignmentResponse close(UUID id, LocalDate closeOn) {
        EmployeeAssignment a = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Assignment not found: " + id));
        ensureEmployeeAccessible(a.getEmployeeId());
        if (a.getEffectiveTo() != null) {
            throw new BadRequestException("Assignment is already closed");
        }
        LocalDate end = closeOn == null ? LocalDate.now() : closeOn;
        if (end.isBefore(a.getEffectiveFrom())) {
            throw new BadRequestException(
                    "closeOn (" + end + ") must be on or after effectiveFrom ("
                            + a.getEffectiveFrom() + ")");
        }
        EmployeeAssignmentResponse before = EmployeeAssignmentResponse.from(a);

        a.setEffectiveTo(end);
        a.setUpdatedBy(currentRequest.username());
        EmployeeAssignment saved = repository.save(a);

        audit.record(MODULE, ENTITY, id.toString(),
                "CLOSE", before, EmployeeAssignmentResponse.from(saved));
        return EmployeeAssignmentResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        EmployeeAssignment a = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Assignment not found: " + id));
        ensureEmployeeAccessible(a.getEmployeeId());
        EmployeeAssignmentResponse before = EmployeeAssignmentResponse.from(a);
        repository.delete(a);
        audit.record(MODULE, ENTITY, id.toString(), "DELETE", before, null);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private BigDecimal normaliseAllocation(BigDecimal value, AssignmentType type) {
        if (value != null) return value;
        // PRIMARY defaults to 100%; non-PRIMARY defaults to 50% to nudge thinking.
        return type == AssignmentType.PRIMARY ? HUNDRED : new BigDecimal("50.00");
    }

    private void ensureAllocationCap(UUID employeeId, BigDecimal newAllocation, UUID excludeId) {
        BigDecimal soFar = repository.sumOpenAllocationForEmployee(employeeId, excludeId);
        BigDecimal total = (soFar == null ? BigDecimal.ZERO : soFar).add(newAllocation);
        if (total.compareTo(HUNDRED) > 0) {
            throw new BadRequestException(
                    "Total open allocation would be " + total + "% — must not exceed 100%");
        }
    }

    private void ensureEmployeeAccessible(UUID employeeId) {
        if (!accessScope.isAccessible(employeeId)) {
            throw new ResourceNotFoundException("Employee not found: " + employeeId);
        }
    }
}
