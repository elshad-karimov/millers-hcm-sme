package az.millers.hcm.lifecycle.service;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.lifecycle.domain.EmployeeMovementRequest;
import az.millers.hcm.lifecycle.domain.MovementRequestStatus;
import az.millers.hcm.lifecycle.domain.MovementType;
import az.millers.hcm.lifecycle.repo.EmployeeMovementRequestRepository;
import az.millers.hcm.staffing.service.PositionHeadcountService;
import az.millers.hcm.staffing.service.PositionOccupancyService;
import az.millers.hcm.staffing.service.StaffingService;

/**
 * M491 — Movement execution wiring. HR executes an APPROVED movement request
 * by applying the proposed changes to the employee record via existing update
 * paths. Salary changes are NOT auto-applied — they go through the dedicated
 * salary-change workflow.
 */
@Service
@Transactional
public class MovementExecutionService {

    private static final Logger log = LoggerFactory.getLogger(MovementExecutionService.class);
    private static final String TENANT = "default";

    private final EmployeeMovementRequestRepository requestRepo;
    private final EmployeeRepository employeeRepo;
    private final PositionHeadcountService headcountGate;
    private final StaffingService staffingService;
    private final PositionOccupancyService occupancyService;
    private final AuditService audit;

    public MovementExecutionService(EmployeeMovementRequestRepository requestRepo,
                                     EmployeeRepository employeeRepo,
                                     PositionHeadcountService headcountGate,
                                     StaffingService staffingService,
                                     PositionOccupancyService occupancyService,
                                     AuditService audit) {
        this.requestRepo = requestRepo;
        this.employeeRepo = employeeRepo;
        this.headcountGate = headcountGate;
        this.staffingService = staffingService;
        this.occupancyService = occupancyService;
        this.audit = audit;
    }

    /**
     * Execute an APPROVED movement request. Applies TRANSFER/PROMOTION changes
     * to employee record. Salary changes are noted but NOT applied — they go
     * through the salary-change approval flow.
     *
     * @return execution message (includes note if salary wasn't applied)
     */
    public String execute(UUID requestId, String executedBy) {
        EmployeeMovementRequest request = requestRepo.findById(requestId)
            .orElseThrow(() -> new ResourceNotFoundException("Movement request not found: " + requestId));

        // Tenant check
        if (!TENANT.equals(request.getTenantId())) {
            throw new ResourceNotFoundException("Movement request not found: " + requestId);
        }

        if (request.getStatus() != MovementRequestStatus.APPROVED) {
            throw new IllegalStateException("Request must be APPROVED to execute. Current status: " + request.getStatus());
        }

        Employee employee = employeeRepo.findById(request.getEmployeeId())
            .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + request.getEmployeeId()));

        StringBuilder executionNote = new StringBuilder();

        // Apply changes based on movement type
        if (request.getMovementType() == MovementType.TRANSFER) {
            executeTransfer(employee, request, executedBy, executionNote);
        } else if (request.getMovementType() == MovementType.PROMOTION) {
            executePromotion(employee, request, executedBy, executionNote);
        }

        // Mark request as EXECUTED
        request.setStatus(MovementRequestStatus.EXECUTED);
        request.setExecutedAt(OffsetDateTime.now());
        request.setExecutedBy(executedBy);
        request.setUpdatedBy(executedBy);
        requestRepo.save(request);

        String message = executionNote.toString();
        audit.record("EMPLOYEE_MOVEMENT", "MovementRequest", requestId.toString(), "EXECUTED",
                    MovementRequestStatus.APPROVED, MovementRequestStatus.EXECUTED);

        log.info("Executed movement request {} for employee {}: {}", requestId, employee.getEmployeeNo(), message);
        return message;
    }

    private void executeTransfer(Employee employee, EmployeeMovementRequest request,
                                  String executedBy, StringBuilder note) {
        boolean changed = false;

        // Update org unit
        if (request.getProposedOrgUnitId() != null &&
            !request.getProposedOrgUnitId().equals(employee.getOrgUnitId())) {
            employee.setOrgUnitId(request.getProposedOrgUnitId());
            changed = true;
            note.append("Transferred to new org unit. ");
        }

        // Update position (with headcount gate)
        if (request.getProposedPositionId() != null &&
            !request.getProposedPositionId().equals(employee.getPositionId())) {
            UUID oldPositionId = employee.getPositionId();
            UUID newPositionId = request.getProposedPositionId();

            // Gate the move
            headcountGate.assertCanMove(oldPositionId, newPositionId);

            employee.setPositionId(newPositionId);
            employee.setUpdatedBy(executedBy);
            employeeRepo.save(employee);

            // Update occupancy (same pattern as EmployeeService.update)
            if (oldPositionId != null) {
                staffingService.adjustOccupancy(oldPositionId, -1,
                    "Transfer (out) for " + employee.getEmployeeNo());
                occupancyService.closeActivePrimary(employee.getId(), oldPositionId,
                    request.getEffectiveDate(),
                    "Transfer to new position for " + employee.getEmployeeNo());
            }
            if (newPositionId != null) {
                staffingService.adjustOccupancy(newPositionId, +1,
                    "Transfer (in) for " + employee.getEmployeeNo());
                occupancyService.openPrimary(employee.getId(), newPositionId,
                    request.getEffectiveDate(),
                    "Transfer from previous position for " + employee.getEmployeeNo());
            }

            changed = true;
            note.append("Transferred to new position. ");
        }

        if (request.getProposedSalary() != null) {
            note.append("NOTE: Salary change NOT applied — use salary-change request flow. ");
        }

        if (changed) {
            employee.setUpdatedBy(executedBy);
            employeeRepo.save(employee);
        }
    }

    private void executePromotion(Employee employee, EmployeeMovementRequest request,
                                   String executedBy, StringBuilder note) {
        boolean changed = false;

        // Update position
        if (request.getProposedPositionId() != null &&
            !request.getProposedPositionId().equals(employee.getPositionId())) {
            UUID oldPositionId = employee.getPositionId();
            UUID newPositionId = request.getProposedPositionId();

            headcountGate.assertCanMove(oldPositionId, newPositionId);

            employee.setPositionId(newPositionId);
            employee.setUpdatedBy(executedBy);
            employeeRepo.save(employee);

            // Update occupancy
            if (oldPositionId != null) {
                staffingService.adjustOccupancy(oldPositionId, -1,
                    "Promotion (out) for " + employee.getEmployeeNo());
                occupancyService.closeActivePrimary(employee.getId(), oldPositionId,
                    request.getEffectiveDate(),
                    "Promotion to new position for " + employee.getEmployeeNo());
            }
            if (newPositionId != null) {
                staffingService.adjustOccupancy(newPositionId, +1,
                    "Promotion (in) for " + employee.getEmployeeNo());
                occupancyService.openPrimary(employee.getId(), newPositionId,
                    request.getEffectiveDate(),
                    "Promotion from previous position for " + employee.getEmployeeNo());
            }

            changed = true;
            note.append("Promoted to new position. ");
        }

        // NOTE: Grade changes tracked in the request but Employee entity doesn't
        // have a grade field in the current model. Grade promotion is implicit
        // via the new position's grade/band.
        if (request.getProposedGrade() != null) {
            note.append(String.format("Grade promotion to %s recorded. ", request.getProposedGrade()));
        }

        if (request.getProposedSalary() != null) {
            note.append("NOTE: Salary change NOT applied — use salary-change request flow. ");
        }

        if (changed) {
            employee.setUpdatedBy(executedBy);
            employeeRepo.save(employee);
        }
    }
}
