package az.millers.hcm.lifecycle.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.corehr.service.EmployeeHistoryService;
import az.millers.hcm.lifecycle.api.dto.ContractChangeResponse;
import az.millers.hcm.lifecycle.api.dto.ContractChangeSubmitRequest;
import az.millers.hcm.lifecycle.domain.ChangeType;
import az.millers.hcm.lifecycle.domain.ContractChange;
import az.millers.hcm.lifecycle.domain.ContractChangeStatus;
import az.millers.hcm.lifecycle.repo.ContractChangeRepository;
import az.millers.hcm.payroll.domain.EmployeeCompensation;
import az.millers.hcm.payroll.service.CompensationService;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.staffing.domain.Position;
import az.millers.hcm.staffing.service.StaffingService;
import az.millers.hcm.workflow.api.dto.StartWorkflowRequest;
import az.millers.hcm.workflow.domain.WorkflowInstance;
import az.millers.hcm.workflow.service.WorkflowService;

/**
 * Contract Change service (PRD 8.12). One service handles all change types
 * (salary / position / department / manager / grade / location / job title /
 * employment type / cost centre); the payload + old snapshot live in JSONB.
 * On apply, SALARY creates a new effective-dated row in payroll; the rest
 * mutate the Employee directly. POSITION additionally swaps staffing
 * occupancy (old −1, new +1) when the linked position changes.
 */
@Service
public class ContractChangeService {

    public static final String WORKFLOW_DEFINITION = "CONTRACT_CHANGE_APPROVAL";
    private static final String MODULE = "LIFECYCLE";
    private static final String ENTITY = "ContractChange";

    private final ContractChangeRepository changes;
    private final EmployeeRepository employees;
    private final WorkflowService workflowService;
    private final CompensationService compensationService;
    private final StaffingService staffingService;
    private final AuditService audit;
    private final CurrentRequest currentRequest;
    private final EmployeeHistoryService historyService;

    public ContractChangeService(ContractChangeRepository changes,
                                  EmployeeRepository employees,
                                  WorkflowService workflowService,
                                  CompensationService compensationService,
                                  StaffingService staffingService,
                                  AuditService audit,
                                  CurrentRequest currentRequest,
                                  EmployeeHistoryService historyService) {
        this.changes = changes;
        this.employees = employees;
        this.workflowService = workflowService;
        this.compensationService = compensationService;
        this.staffingService = staffingService;
        this.audit = audit;
        this.currentRequest = currentRequest;
        this.historyService = historyService;
    }

    @Transactional(readOnly = true)
    public ContractChange get(UUID id) {
        return changes.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contract change not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<ContractChange> list(UUID employeeId, ChangeType type, ContractChangeStatus status,
                                       Pageable pageable) {
        if (employeeId != null) return changes.findByEmployeeIdOrderByCreatedAtDesc(employeeId, pageable);
        if (type != null)       return changes.findByChangeTypeOrderByCreatedAtDesc(type, pageable);
        if (status != null)     return changes.findByStatusOrderByCreatedAtDesc(status, pageable);
        return changes.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional
    public ContractChange submit(ContractChangeSubmitRequest req) {
        Employee employee = employees.findById(req.employeeId())
                .orElseThrow(() -> new BadRequestException("Employee not found: " + req.employeeId()));
        validatePayload(req.changeType(), req.newValue());

        ContractChange c = new ContractChange();
        c.setChangeNo(String.format("CC-%05d", changes.nextNoSequence()));
        c.setEmployeeId(employee.getId());
        c.setChangeType(req.changeType());
        c.setEffectiveDate(req.effectiveDate());
        c.setReason(req.reason());
        c.setNewValue(req.newValue());
        c.setOldValue(snapshotOldValue(employee, req.changeType()));
        c.setAttachmentUrls(req.attachmentUrls());
        c.setNote(req.note());
        c.setStatus(ContractChangeStatus.PENDING);
        c.setCreatedBy(currentRequest.username());
        ContractChange saved = changes.save(c);

        WorkflowInstance instance = workflowService.start(new StartWorkflowRequest(
                WORKFLOW_DEFINITION,
                MODULE,
                ENTITY,
                saved.getId().toString(),
                saved.getChangeNo() + " — " + employee.getFirstName() + " " + employee.getLastName()
                        + " (" + saved.getChangeType() + ", effective " + saved.getEffectiveDate() + ")",
                Map.of(
                        "employeeNo", employee.getEmployeeNo(),
                        "employeeName", employee.getFirstName() + " " + employee.getLastName(),
                        "changeType", saved.getChangeType().name(),
                        "effectiveDate", saved.getEffectiveDate().toString())));
        saved.setWorkflowInstanceId(instance.getId());
        saved = changes.save(saved);

        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "SUBMIT", null, ContractChangeResponse.from(saved));
        return saved;
    }

    // ---------- Workflow callbacks ----------

    @Transactional
    public ContractChange onApproved(UUID id, String comment) {
        ContractChange c = get(id);
        if (c.getStatus() != ContractChangeStatus.PENDING) return c;
        c.setStatus(ContractChangeStatus.APPROVED);
        ContractChange saved = changes.save(c);
        audit.record(MODULE, ENTITY, id.toString(), "APPROVED", null,
                Map.of("comment", comment == null ? "" : comment));
        // Auto-apply on approval keeps the engine simple; if HR needs to stage
        // applications later this can split into a separate /apply endpoint.
        return apply(saved.getId());
    }

    @Transactional
    public ContractChange onRejected(UUID id, String comment) {
        ContractChange c = get(id);
        if (c.getStatus() != ContractChangeStatus.PENDING) return c;
        c.setStatus(ContractChangeStatus.REJECTED);
        ContractChange saved = changes.save(c);
        audit.record(MODULE, ENTITY, id.toString(), "REJECTED", null,
                Map.of("comment", comment == null ? "" : comment));
        return saved;
    }

    @Transactional
    public ContractChange onCancelled(UUID id, String comment) {
        ContractChange c = get(id);
        if (c.getStatus() != ContractChangeStatus.PENDING) return c;
        c.setStatus(ContractChangeStatus.CANCELLED);
        ContractChange saved = changes.save(c);
        audit.record(MODULE, ENTITY, id.toString(), "CANCELLED", null,
                Map.of("comment", comment == null ? "" : comment));
        return saved;
    }

    // ---------- Apply ----------

    @Transactional
    public ContractChange apply(UUID id) {
        ContractChange c = get(id);
        if (c.getStatus() != ContractChangeStatus.APPROVED) {
            throw new BadRequestException("Only APPROVED changes can be applied");
        }
        Employee employee = employees.findById(c.getEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee not found: " + c.getEmployeeId()));
        ContractChangeResponse before = ContractChangeResponse.from(c);
        Map<String, Object> v = c.getNewValue();

        switch (c.getChangeType()) {
            case SALARY -> applySalary(employee, c.getEffectiveDate(), v, c);
            case POSITION -> applyPosition(employee, v);
            case DEPARTMENT -> {
                employee.setOrgUnitId(uuidOrNull(v.get("orgUnitId")));
                employee.setDepartmentName(strOrNull(v.get("departmentName")));
            }
            case MANAGER -> {
                UUID managerId = uuidOrNull(v.get("managerId"));
                if (managerId != null && managerId.equals(employee.getId())) {
                    throw new BadRequestException("An employee cannot be their own manager");
                }
                employee.setManagerId(managerId);
            }
            case GRADE      -> { /* grade lives on the Position; nothing to do on Employee */ }
            case LOCATION   -> { /* tracked at position/site; placeholder */ }
            case JOB_TITLE  -> employee.setPositionTitle(strOrNull(v.get("positionTitle")));
            case EMPLOYMENT_TYPE -> { /* lives on the Position */ }
            case COST_CENTRE -> employee.setCostCentre(strOrNull(v.get("costCentre")));
        }

        if (c.getChangeType() != ChangeType.SALARY) {
            employee.setUpdatedBy(currentRequest.username());
            employees.save(employee);
        }

        // M62 / P1-10: record a new employment-history slice for every change
        // type that mutates the employment aggregate. SALARY is already
        // effective-dated in payroll.employee_compensation, so we skip it here;
        // GRADE / EMPLOYMENT_TYPE / LOCATION currently no-op on the Employee
        // entity (see switch above) but the slice still captures the audit
        // intent in the history table.
        if (writesEmploymentSlice(c.getChangeType())) {
            historyService.recordEmploymentSlice(
                    employee,
                    c.getEffectiveDate(),
                    c.getChangeType().name()
                            + (c.getReason() == null ? "" : " — " + c.getReason()),
                    MODULE, ENTITY, c.getId().toString());
        }

        c.setStatus(ContractChangeStatus.APPLIED);
        c.setAppliedAt(OffsetDateTime.now());
        c.setAppliedBy(currentRequest.username());
        ContractChange saved = changes.save(c);
        audit.record(MODULE, ENTITY, id.toString(), "APPLIED", before,
                ContractChangeResponse.from(saved));
        return saved;
    }

    /**
     * SALARY changes are tracked in {@code payroll.employee_compensation} —
     * they already have effective-dated history of their own. All other
     * change types are state on the Employee aggregate and need a new
     * slice in {@code core_hr.employee_employment_history}.
     */
    private boolean writesEmploymentSlice(ChangeType type) {
        return type != ChangeType.SALARY;
    }

    // ---------- Internals ----------

    private void applySalary(Employee employee, LocalDate effective, Map<String, Object> v,
                              ContractChange c) {
        Object amt = v.get("monthlyBaseSalary");
        if (amt == null) {
            throw new BadRequestException("SALARY change requires monthlyBaseSalary");
        }
        BigDecimal salary = new BigDecimal(amt.toString());
        String currency = strOrNull(v.get("currency"));
        EmployeeCompensation comp = new EmployeeCompensation();
        comp.setEmployeeId(employee.getId());
        comp.setMonthlyBaseSalary(salary);
        comp.setCurrency(currency == null ? "AZN" : currency);
        comp.setEffectiveFrom(effective);
        comp.setReason("Contract change " + c.getChangeNo()
                + (c.getReason() == null ? "" : " — " + c.getReason()));
        compensationService.upsert(comp);
    }

    private void applyPosition(Employee employee, Map<String, Object> v) {
        UUID newPositionId = uuidOrNull(v.get("positionId"));
        if (newPositionId == null) {
            throw new BadRequestException("POSITION change requires positionId");
        }
        Position newPos = staffingService.get(newPositionId);
        UUID oldPositionId = employee.getPositionId();
        if (oldPositionId != null && !oldPositionId.equals(newPositionId)) {
            try {
                staffingService.adjustOccupancy(oldPositionId, -1,
                        "Position change for " + employee.getEmployeeNo());
            } catch (RuntimeException ex) {
                // not fatal — old position may already be closed
            }
        }
        if (!newPositionId.equals(oldPositionId)) {
            staffingService.adjustOccupancy(newPositionId, +1,
                    "Position change for " + employee.getEmployeeNo());
        }
        employee.setPositionId(newPositionId);
        employee.setPositionTitle(newPos.getTitle());
        if (newPos.getOrgUnitId() != null) {
            employee.setOrgUnitId(newPos.getOrgUnitId());
        }
        if (newPos.getOrgUnitLabel() != null) {
            employee.setDepartmentName(newPos.getOrgUnitLabel());
        }
        if (newPos.getCostCentre() != null) {
            employee.setCostCentre(newPos.getCostCentre());
        }
    }

    private void validatePayload(ChangeType type, Map<String, Object> v) {
        switch (type) {
            case SALARY -> {
                if (v.get("monthlyBaseSalary") == null) {
                    throw new BadRequestException("SALARY change requires monthlyBaseSalary");
                }
            }
            case POSITION -> {
                if (v.get("positionId") == null) {
                    throw new BadRequestException("POSITION change requires positionId");
                }
            }
            case DEPARTMENT -> {
                if (v.get("orgUnitId") == null && v.get("departmentName") == null) {
                    throw new BadRequestException(
                            "DEPARTMENT change requires orgUnitId or departmentName");
                }
            }
            case MANAGER -> {
                if (v.get("managerId") == null) {
                    throw new BadRequestException("MANAGER change requires managerId");
                }
            }
            case JOB_TITLE -> {
                if (v.get("positionTitle") == null) {
                    throw new BadRequestException("JOB_TITLE change requires positionTitle");
                }
            }
            case COST_CENTRE -> {
                if (v.get("costCentre") == null) {
                    throw new BadRequestException("COST_CENTRE change requires costCentre");
                }
            }
            default -> { /* GRADE, LOCATION, EMPLOYMENT_TYPE — free-form payload */ }
        }
    }

    private Map<String, Object> snapshotOldValue(Employee e, ChangeType type) {
        Map<String, Object> m = new LinkedHashMap<>();
        switch (type) {
            case SALARY -> {
                EmployeeCompensation existing = null;
                try {
                    existing = compensationService.getActiveOn(e.getId(), LocalDate.now());
                } catch (RuntimeException ignored) { /* none — leave null */ }
                if (existing != null) {
                    m.put("monthlyBaseSalary", existing.getMonthlyBaseSalary().toPlainString());
                    m.put("currency", existing.getCurrency());
                }
            }
            case POSITION -> {
                m.put("positionId", str(e.getPositionId()));
                m.put("positionTitle", e.getPositionTitle());
            }
            case DEPARTMENT -> {
                m.put("orgUnitId", str(e.getOrgUnitId()));
                m.put("departmentName", e.getDepartmentName());
            }
            case MANAGER     -> m.put("managerId", str(e.getManagerId()));
            case GRADE       -> { /* lives on the position, intentionally blank */ }
            case LOCATION    -> { /* not yet on Employee */ }
            case JOB_TITLE   -> m.put("positionTitle", e.getPositionTitle());
            case EMPLOYMENT_TYPE -> { /* lives on the position */ }
            case COST_CENTRE -> m.put("costCentre", e.getCostCentre());
        }
        return m;
    }

    private static UUID uuidOrNull(Object o) {
        if (o == null) return null;
        String s = o.toString();
        return s.isBlank() ? null : UUID.fromString(s);
    }

    private static String strOrNull(Object o) {
        return o == null ? null : o.toString();
    }

    private static String str(UUID id) {
        return id == null ? null : id.toString();
    }
}
