package az.millers.hcm.attendance.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import az.millers.hcm.attendance.api.dto.OvertimeDtos.OvertimeDecision;
import az.millers.hcm.attendance.api.dto.OvertimeDtos.OvertimeRequestDto;
import az.millers.hcm.attendance.api.dto.OvertimeDtos.OvertimeResponse;
import az.millers.hcm.attendance.domain.DailySummary;
import az.millers.hcm.attendance.domain.OvertimeRequest;
import az.millers.hcm.attendance.events.OvertimeApprovedEvent;
import az.millers.hcm.attendance.events.OvertimeRequestSubmittedEvent;
import az.millers.hcm.attendance.repo.DailySummaryRepository;
import az.millers.hcm.attendance.repo.OvertimeRequestRepository;
import az.millers.hcm.audit.AuditService;
import az.millers.hcm.organization.repo.LegalEntityRepository;
import az.millers.hcm.workflow.api.dto.StartWorkflowRequest;
import az.millers.hcm.workflow.service.WorkflowService;

/**
 * M329: Overtime request service.
 *
 * <p>Manages overtime requests (pre-approval and post-recording), workflow approval,
 * and updates daily summary when approved.
 */
@Service
@Transactional
public class OvertimeRequestService {

    private static final String MODULE = "attendance";
    private static final String ENTITY_TYPE = "overtime_request";

    private final OvertimeRequestRepository repository;
    private final DailySummaryRepository summaryRepository;
    private final WorkflowService workflowService;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final LegalEntityRepository legalEntities;

    public OvertimeRequestService(OvertimeRequestRepository repository,
                                   DailySummaryRepository summaryRepository,
                                   WorkflowService workflowService,
                                   AuditService auditService,
                                   ApplicationEventPublisher eventPublisher,
                                   LegalEntityRepository legalEntities) {
        this.repository = repository;
        this.summaryRepository = summaryRepository;
        this.workflowService = workflowService;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.legalEntities = legalEntities;
    }

    public OvertimeResponse submit(OvertimeRequestDto req, String submittedBy) {
        UUID tenantId = defaultTenantId();
        int requestedMinutes = (int) Duration.between(req.otStart(), req.otEnd()).toMinutes();

        OvertimeRequest entity = new OvertimeRequest();
        entity.setTenantId(tenantId);
        entity.setEmployeeId(req.employeeId());
        entity.setWorkDate(req.workDate());
        entity.setSummaryId(req.summaryId());
        entity.setOtStart(req.otStart());
        entity.setOtEnd(req.otEnd());
        entity.setRequestedMinutes(requestedMinutes);
        entity.setReason(req.reason());
        entity.setPreApproved(req.preApproved());
        entity.setWorkflowStatus("PENDING");
        entity.setCreatedBy(submittedBy);
        entity.setUpdatedBy(submittedBy);

        entity = repository.save(entity);

        String title = "Overtime request: " + req.employeeId() + " on " + req.workDate()
                + " (" + requestedMinutes + " min)";
        Map<String, Object> context = Map.of("overtimeMinutes", requestedMinutes);

        workflowService.start(new StartWorkflowRequest(
                "OVERTIME_REQUEST",
                MODULE,
                ENTITY_TYPE,
                entity.getId().toString(),
                title,
                context));

        auditService.record(MODULE, ENTITY_TYPE, entity.getId().toString(),
                "OT_REQUEST_SUBMITTED", null,
                Map.of("workDate", req.workDate().toString(), "minutes", requestedMinutes));

        eventPublisher.publishEvent(new OvertimeRequestSubmittedEvent(
                entity.getId(), req.employeeId(), tenantId, requestedMinutes, submittedBy));

        return OvertimeResponse.from(entity);
    }

    @Transactional(readOnly = true)
    public List<OvertimeResponse> list() {
        UUID tenantId = defaultTenantId();
        return repository.findByTenantIdAndWorkflowStatusOrderByCreatedAtDesc(tenantId, "PENDING").stream()
                .map(OvertimeResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OvertimeResponse> listByEmployee(UUID employeeId) {
        UUID tenantId = defaultTenantId();
        return repository.findByTenantIdAndEmployeeIdOrderByWorkDateDesc(tenantId, employeeId).stream()
                .map(OvertimeResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public OvertimeResponse get(UUID id) {
        UUID tenantId = defaultTenantId();
        OvertimeRequest entity = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Overtime request not found: " + id));
        return OvertimeResponse.from(entity);
    }

    public OvertimeResponse decide(UUID id, String decision, String comment, String decidedBy) {
        UUID tenantId = defaultTenantId();
        OvertimeRequest entity = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Overtime request not found: " + id));

        if ("APPROVED".equals(entity.getWorkflowStatus()) || "REJECTED".equals(entity.getWorkflowStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Request already decided: " + entity.getWorkflowStatus());
        }

        entity.setWorkflowStatus(decision);
        entity.setDecision(decision);
        entity.setDecisionComment(comment);
        entity.setDecidedAt(OffsetDateTime.now());
        entity.setDecidedBy(decidedBy);
        entity.setUpdatedBy(decidedBy);

        OvertimeRequest saved = repository.save(entity);

        if ("APPROVED".equals(decision) && !saved.isPreApproved() && saved.getSummaryId() != null) {
            final int requestedMinutes = saved.getRequestedMinutes();
            summaryRepository.findById(saved.getSummaryId()).ifPresent(summary -> {
                summary.setOvertimeMinutes(summary.getOvertimeMinutes() + requestedMinutes);
                summaryRepository.save(summary);
            });

            auditService.record(MODULE, ENTITY_TYPE, id.toString(),
                    "OT_REQUEST_APPROVED", null,
                    Map.of("minutes", saved.getRequestedMinutes(), "comment", comment != null ? comment : ""));

            eventPublisher.publishEvent(new OvertimeApprovedEvent(
                    id, saved.getEmployeeId(), tenantId, decidedBy));
        } else if ("REJECTED".equals(decision)) {
            auditService.record(MODULE, ENTITY_TYPE, id.toString(),
                    "OT_REQUEST_REJECTED", null,
                    Map.of("comment", comment != null ? comment : ""));
        }

        return OvertimeResponse.from(saved);
    }

    private UUID defaultTenantId() {
        return legalEntities.findAllByOrderByCodeAsc().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No legal entity found")).getId();
    }
}
