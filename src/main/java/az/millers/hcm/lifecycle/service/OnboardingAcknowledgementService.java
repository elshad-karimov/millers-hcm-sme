package az.millers.hcm.lifecycle.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.lifecycle.api.dto.ChecklistDtos.UpdateTaskRequest;
import az.millers.hcm.lifecycle.api.dto.OnboardingDtos.AcknowledgementResponse;
import az.millers.hcm.lifecycle.domain.AcknowledgementKind;
import az.millers.hcm.lifecycle.domain.ChecklistAssignment;
import az.millers.hcm.lifecycle.domain.ChecklistTaskStatus;
import az.millers.hcm.lifecycle.domain.ChecklistTaskStatusValue;
import az.millers.hcm.lifecycle.domain.OnboardingAcknowledgement;
import az.millers.hcm.lifecycle.repo.ChecklistAssignmentRepository;
import az.millers.hcm.lifecycle.repo.ChecklistTaskStatusRepository;
import az.millers.hcm.lifecycle.repo.OnboardingAcknowledgementRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * Records policy / contract / document acknowledgements against onboarding
 * checklist tasks (M304 — Phase B.4). The acknowledgement is the compliance
 * proof; recording it marks the originating checklist task DONE.
 */
@Service
public class OnboardingAcknowledgementService {

    private static final String MODULE = "LIFECYCLE";
    private static final String ENTITY = "OnboardingAcknowledgement";

    private final OnboardingAcknowledgementRepository acks;
    private final ChecklistTaskStatusRepository taskStatuses;
    private final ChecklistAssignmentRepository assignments;
    private final ChecklistService checklistService;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public OnboardingAcknowledgementService(OnboardingAcknowledgementRepository acks,
                                            ChecklistTaskStatusRepository taskStatuses,
                                            ChecklistAssignmentRepository assignments,
                                            ChecklistService checklistService,
                                            AuditService audit,
                                            CurrentRequest currentRequest) {
        this.acks = acks;
        this.taskStatuses = taskStatuses;
        this.assignments = assignments;
        this.checklistService = checklistService;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional
    public AcknowledgementResponse acknowledge(UUID taskStatusId, String statement, String reference) {
        ChecklistTaskStatus ts = taskStatuses.findById(taskStatusId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskStatusId));
        AcknowledgementKind kind = AcknowledgementKind.forTaskType(ts.getTaskType())
                .orElseThrow(() -> new BadRequestException(
                        "Task type " + ts.getTaskType() + " is not acknowledgeable"));
        if (acks.existsByTaskStatusId(taskStatusId)) {
            throw new BadRequestException("This task has already been acknowledged");
        }
        ChecklistAssignment a = assignments.findById(ts.getAssignmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found"));

        OnboardingAcknowledgement ack = new OnboardingAcknowledgement();
        ack.setEmployeeId(a.getEmployeeId());
        ack.setAssignmentId(a.getId());
        ack.setTaskStatusId(taskStatusId);
        ack.setKind(kind);
        ack.setStatement(blankToNull(statement));
        ack.setReference(blankToNull(reference));
        ack.setAcknowledgedBy(currentRequest.username());
        acks.save(ack);

        // The acknowledgement completes the task.
        checklistService.updateTask(taskStatusId, new UpdateTaskRequest(
                ChecklistTaskStatusValue.DONE, null, null,
                kind + " acknowledged" + (reference == null || reference.isBlank() ? "" : ": " + reference)));

        audit.record(MODULE, ENTITY, ack.getId().toString(), "ACKNOWLEDGE", null,
                Map.of("kind", kind.name(), "taskStatusId", taskStatusId.toString(),
                        "employeeId", a.getEmployeeId().toString()));
        return toResponse(ack);
    }

    @Transactional(readOnly = true)
    public List<AcknowledgementResponse> listForEmployee(UUID employeeId) {
        return acks.findByEmployeeIdOrderByAcknowledgedAtDesc(employeeId)
                .stream().map(this::toResponse).toList();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private AcknowledgementResponse toResponse(OnboardingAcknowledgement a) {
        return new AcknowledgementResponse(
                a.getId(), a.getEmployeeId(), a.getAssignmentId(), a.getTaskStatusId(),
                a.getKind().name(), a.getStatement(), a.getReference(),
                a.getAcknowledgedBy(), a.getAcknowledgedAt());
    }
}
