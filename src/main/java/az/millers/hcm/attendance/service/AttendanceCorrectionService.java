package az.millers.hcm.attendance.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import az.millers.hcm.attendance.api.dto.CorrectionDtos.CorrectionDecision;
import az.millers.hcm.attendance.api.dto.CorrectionDtos.CorrectionRequest;
import az.millers.hcm.attendance.api.dto.CorrectionDtos.CorrectionResponse;
import az.millers.hcm.attendance.domain.AttendanceCorrectionRequest;
import az.millers.hcm.attendance.events.CorrectionApprovedEvent;
import az.millers.hcm.attendance.events.CorrectionRejectedEvent;
import az.millers.hcm.attendance.events.CorrectionSubmittedEvent;
import az.millers.hcm.attendance.repo.AttendanceCorrectionRepository;
import az.millers.hcm.audit.AuditService;
import az.millers.hcm.organization.repo.LegalEntityRepository;
import az.millers.hcm.workflow.api.dto.StartWorkflowRequest;
import az.millers.hcm.workflow.service.WorkflowService;
import org.springframework.http.HttpStatus;

/**
 * M328: Attendance correction request service.
 *
 * <p>Submits corrections to workflow, manages approvals, triggers attendance recomputation.
 */
@Service
@Transactional
public class AttendanceCorrectionService {

    private static final String MODULE = "attendance";
    private static final String ENTITY_TYPE = "attendance_correction_request";

    private final AttendanceCorrectionRepository repository;
    private final WorkflowService workflowService;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final AttendanceEngine attendanceEngine;
    private final LegalEntityRepository legalEntities;

    public AttendanceCorrectionService(AttendanceCorrectionRepository repository,
                                        WorkflowService workflowService,
                                        AuditService auditService,
                                        ApplicationEventPublisher eventPublisher,
                                        AttendanceEngine attendanceEngine,
                                        LegalEntityRepository legalEntities) {
        this.repository = repository;
        this.workflowService = workflowService;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.attendanceEngine = attendanceEngine;
        this.legalEntities = legalEntities;
    }

    public CorrectionResponse submit(CorrectionRequest req, String submittedBy) {
        UUID tenantId = defaultTenantId();
        AttendanceCorrectionRequest entity = new AttendanceCorrectionRequest();
        entity.setTenantId(tenantId);
        entity.setEmployeeId(req.employeeId());
        entity.setWorkDate(req.workDate());
        entity.setSummaryId(req.summaryId());
        entity.setRequestedClockIn(req.requestedClockIn());
        entity.setRequestedClockOut(req.requestedClockOut());
        entity.setRequestedStatus(req.requestedStatus());
        entity.setReason(req.reason());
        entity.setCorrectionType(req.correctionType() != null ? req.correctionType() : "CLOCK_TIME");
        entity.setAbsenceStatusChanged(req.absenceStatusChanged());
        entity.setOvertimeDeltaMinutes(req.overtimeDeltaMinutes());
        entity.setWorkflowStatus("PENDING");
        entity.setCreatedBy(submittedBy);
        entity.setUpdatedBy(submittedBy);

        entity = repository.save(entity);

        String title = "Attendance correction: " + req.employeeId() + " on " + req.workDate();
        Map<String, Object> context = Map.of(
                "absenceStatusChanged", req.absenceStatusChanged(),
                "overtimeDeltaMinutes", req.overtimeDeltaMinutes());

        workflowService.start(new StartWorkflowRequest(
                "ATTENDANCE_CORRECTION",
                MODULE,
                ENTITY_TYPE,
                entity.getId().toString(),
                title,
                context));

        auditService.record(MODULE, ENTITY_TYPE, entity.getId().toString(),
                "CORRECTION_SUBMITTED", null, Map.of("workDate", req.workDate().toString()));

        eventPublisher.publishEvent(new CorrectionSubmittedEvent(
                entity.getId(), req.employeeId(), tenantId, submittedBy));

        return CorrectionResponse.from(entity);
    }

    @Transactional(readOnly = true)
    public List<CorrectionResponse> list() {
        UUID tenantId = defaultTenantId();
        return repository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(CorrectionResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CorrectionResponse> listByEmployee(UUID employeeId) {
        UUID tenantId = defaultTenantId();
        return repository.findByTenantIdAndEmployeeIdOrderByWorkDateDesc(tenantId, employeeId).stream()
                .map(CorrectionResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CorrectionResponse get(UUID id) {
        UUID tenantId = defaultTenantId();
        AttendanceCorrectionRequest entity = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Correction request not found: " + id));
        return CorrectionResponse.from(entity);
    }

    public CorrectionResponse decide(UUID id, String decision, String comment, String decidedBy) {
        UUID tenantId = defaultTenantId();
        AttendanceCorrectionRequest entity = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Correction request not found: " + id));

        if ("APPROVED".equals(entity.getWorkflowStatus()) || "REJECTED".equals(entity.getWorkflowStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Correction already decided: " + entity.getWorkflowStatus());
        }

        entity.setWorkflowStatus(decision);
        entity.setDecision(decision);
        entity.setDecisionComment(comment);
        entity.setDecidedAt(OffsetDateTime.now());
        entity.setDecidedBy(decidedBy);
        entity.setUpdatedBy(decidedBy);

        entity = repository.save(entity);

        if ("APPROVED".equals(decision)) {
            attendanceEngine.computeFor(entity.getEmployeeId(), entity.getWorkDate(), tenantId);
            auditService.record(MODULE, ENTITY_TYPE, id.toString(),
                    "CORRECTION_APPROVED", null, Map.of("comment", comment != null ? comment : ""));
            eventPublisher.publishEvent(new CorrectionApprovedEvent(
                    id, entity.getEmployeeId(), tenantId, decidedBy));
        } else {
            auditService.record(MODULE, ENTITY_TYPE, id.toString(),
                    "CORRECTION_REJECTED", null, Map.of("comment", comment != null ? comment : ""));
            eventPublisher.publishEvent(new CorrectionRejectedEvent(
                    id, entity.getEmployeeId(), tenantId, decidedBy));
        }

        return CorrectionResponse.from(entity);
    }

    private UUID defaultTenantId() {
        return legalEntities.findAllByOrderByCodeAsc().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No legal entity found")).getId();
    }
}
