package az.millers.hcm.attendance.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.attendance.api.dto.ScheduleAssignmentRequest;
import az.millers.hcm.attendance.api.dto.ScheduleAssignmentResponse;
import az.millers.hcm.attendance.api.dto.WorkScheduleRequest;
import az.millers.hcm.attendance.api.dto.WorkScheduleResponse;
import az.millers.hcm.attendance.domain.ScheduleAssignment;
import az.millers.hcm.attendance.domain.WorkSchedule;
import az.millers.hcm.attendance.repo.ScheduleAssignmentRepository;
import az.millers.hcm.attendance.repo.WorkScheduleRepository;
import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.security.CurrentRequest;

@Service
public class WorkScheduleService {

    private static final String MODULE = "ATTENDANCE";

    private final WorkScheduleRepository schedules;
    private final ScheduleAssignmentRepository assignments;
    private final EmployeeRepository employees;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public WorkScheduleService(WorkScheduleRepository schedules,
                                ScheduleAssignmentRepository assignments,
                                EmployeeRepository employees,
                                AuditService audit,
                                CurrentRequest currentRequest) {
        this.schedules = schedules;
        this.assignments = assignments;
        this.employees = employees;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<WorkSchedule> list() {
        return schedules.findAll();
    }

    @Transactional(readOnly = true)
    public WorkSchedule get(UUID id) {
        return schedules.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found: " + id));
    }

    @Transactional
    public WorkSchedule create(WorkScheduleRequest req) {
        if (schedules.existsByCode(req.code())) {
            throw new BadRequestException("Schedule code already exists: " + req.code());
        }
        validate(req);
        WorkSchedule s = new WorkSchedule();
        applyRequest(s, req);
        s.setCreatedBy(currentRequest.username());
        s.setUpdatedBy(currentRequest.username());
        WorkSchedule saved = schedules.save(s);
        audit.record(MODULE, "WorkSchedule", saved.getId().toString(),
                "CREATE", null, WorkScheduleResponse.from(saved));
        return saved;
    }

    @Transactional
    public WorkSchedule update(UUID id, WorkScheduleRequest req) {
        WorkSchedule s = get(id);
        if (!s.getCode().equals(req.code()) && schedules.existsByCode(req.code())) {
            throw new BadRequestException("Schedule code already exists: " + req.code());
        }
        validate(req);
        WorkScheduleResponse before = WorkScheduleResponse.from(s);
        applyRequest(s, req);
        s.setUpdatedBy(currentRequest.username());
        WorkSchedule saved = schedules.save(s);
        audit.record(MODULE, "WorkSchedule", id.toString(),
                "UPDATE", before, WorkScheduleResponse.from(saved));
        return saved;
    }

    // ---------- Assignments ----------

    @Transactional(readOnly = true)
    public List<ScheduleAssignment> assignmentsFor(UUID employeeId) {
        return assignments.findByEmployeeIdOrderByEffectiveFromDesc(employeeId);
    }

    @Transactional
    public ScheduleAssignment assign(ScheduleAssignmentRequest req) {
        if (!employees.existsById(req.employeeId())) {
            throw new BadRequestException("Employee not found: " + req.employeeId());
        }
        if (!schedules.existsById(req.scheduleId())) {
            throw new BadRequestException("Schedule not found: " + req.scheduleId());
        }
        if (req.effectiveTo() != null && req.effectiveTo().isBefore(req.effectiveFrom())) {
            throw new BadRequestException("effectiveTo cannot be earlier than effectiveFrom");
        }
        ScheduleAssignment a = new ScheduleAssignment();
        a.setEmployeeId(req.employeeId());
        a.setScheduleId(req.scheduleId());
        a.setEffectiveFrom(req.effectiveFrom());
        a.setEffectiveTo(req.effectiveTo());
        a.setCreatedBy(currentRequest.username());
        ScheduleAssignment saved = assignments.save(a);
        audit.record(MODULE, "ScheduleAssignment", saved.getId().toString(),
                "CREATE", null, ScheduleAssignmentResponse.from(saved));
        return saved;
    }

    private void applyRequest(WorkSchedule s, WorkScheduleRequest req) {
        s.setCode(req.code());
        s.setName(req.name());
        s.setScheduleType(req.scheduleType());
        s.setWorkStart(req.workStart());
        s.setWorkEnd(req.workEnd());
        s.setBreakMinutes(req.breakMinutes() == null ? 0 : req.breakMinutes());
        s.setGracePeriodMinutes(req.gracePeriodMinutes() == null ? 0 : req.gracePeriodMinutes());
        s.setWorkDays(req.workDays());
        s.setOvertimeThresholdMinutes(req.overtimeThresholdMinutes());
        s.setActive(req.active() == null ? true : req.active());
    }

    private void validate(WorkScheduleRequest req) {
        if (!req.workStart().isBefore(req.workEnd())) {
            throw new BadRequestException(
                    "workStart must be before workEnd (cross-midnight shifts come with the NIGHT schedule type, later)");
        }
    }
}
