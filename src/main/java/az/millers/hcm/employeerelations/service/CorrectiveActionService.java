package az.millers.hcm.employeerelations.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.employeerelations.domain.CorrectiveActionPlan;
import az.millers.hcm.employeerelations.domain.CorrectiveActionStatus;
import az.millers.hcm.employeerelations.repo.CorrectiveActionPlanRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * M447 — Corrective action plan service.
 * Daily sweep marks past-due OPEN/IN_PROGRESS plans OVERDUE.
 */
@Service
public class CorrectiveActionService {

    private static final String TENANT = "default";
    private static final String MODULE = "employee-relations";
    private static final String ENTITY = "CorrectiveActionPlan";

    private final CorrectiveActionPlanRepository repo;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public CorrectiveActionService(CorrectiveActionPlanRepository repo,
                                   AuditService audit,
                                   CurrentRequest currentRequest) {
        this.repo = repo;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional
    public CorrectiveActionPlan create(UUID erCaseId,
                                       UUID disciplinaryActionId,
                                       UUID employeeId,
                                       String actionRequired,
                                       String responsibleUsername,
                                       LocalDate dueDate,
                                       LocalDate followUpDate) {
        CorrectiveActionPlan plan = new CorrectiveActionPlan();
        plan.setTenantId(TENANT);
        plan.setErCaseId(erCaseId);
        plan.setDisciplinaryActionId(disciplinaryActionId);
        plan.setEmployeeId(employeeId);
        plan.setActionRequired(actionRequired);
        plan.setResponsibleUsername(responsibleUsername);
        plan.setDueDate(dueDate);
        plan.setFollowUpDate(followUpDate);
        plan.setStatus(CorrectiveActionStatus.OPEN);
        plan.setCreatedBy(currentRequest.username());
        plan.setUpdatedBy(currentRequest.username());

        CorrectiveActionPlan saved = repo.save(plan);

        audit.record(MODULE, ENTITY, saved.getId().toString(), "CREATED", null, null);

        return saved;
    }

    @Transactional
    public CorrectiveActionPlan updateStatus(UUID id, CorrectiveActionStatus newStatus, String notes) {
        CorrectiveActionPlan plan = get(id);

        CorrectiveActionStatus oldStatus = plan.getStatus();
        plan.setStatus(newStatus);
        plan.setUpdatedBy(currentRequest.username());
        plan.setUpdatedAt(OffsetDateTime.now());

        if (notes != null) {
            plan.setNotes(notes);
        }

        if (newStatus == CorrectiveActionStatus.COMPLETED) {
            plan.setCompletedAt(OffsetDateTime.now());
        }

        CorrectiveActionPlan updated = repo.save(plan);

        audit.record(MODULE, ENTITY, id.toString(), "STATUS_CHANGE",
                Map.of("status", oldStatus),
                Map.of("status", newStatus));

        return updated;
    }

    @Transactional(readOnly = true)
    public CorrectiveActionPlan get(UUID id) {
        return repo.findByIdAndTenantId(id, TENANT)
                .orElseThrow(() -> new ResourceNotFoundException("Corrective action plan not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<CorrectiveActionPlan> list(CorrectiveActionStatus statusFilter,
                                           String responsibleUsername) {
        if (statusFilter != null) {
            return repo.findByTenantIdAndStatusInOrderByDueDateAsc(TENANT, List.of(statusFilter));
        } else if (responsibleUsername != null) {
            return repo.findByTenantIdAndResponsibleUsernameOrderByDueDateAsc(TENANT, responsibleUsername);
        } else {
            return repo.findByTenantIdOrderByDueDateAsc(TENANT);
        }
    }

    /**
     * M447 — Daily sweep at 05:45 to mark past-due plans OVERDUE.
     * Idempotent, audited.
     */
    @Scheduled(cron = "0 45 5 * * *")
    @Transactional
    public void markOverduePlans() {
        LocalDate today = LocalDate.now();
        List<CorrectiveActionStatus> activateStatuses = List.of(
                CorrectiveActionStatus.OPEN,
                CorrectiveActionStatus.IN_PROGRESS);

        List<CorrectiveActionPlan> overdue = repo.findOverdue(TENANT, activateStatuses, today);

        for (CorrectiveActionPlan plan : overdue) {
            if (plan.getStatus() != CorrectiveActionStatus.OVERDUE) {
                CorrectiveActionStatus oldStatus = plan.getStatus();
                plan.setStatus(CorrectiveActionStatus.OVERDUE);
                plan.setUpdatedBy("system");
                plan.setUpdatedAt(OffsetDateTime.now());
                repo.save(plan);

                audit.record(MODULE, ENTITY, plan.getId().toString(), "MARKED_OVERDUE",
                        Map.of("status", oldStatus),
                        Map.of("status", CorrectiveActionStatus.OVERDUE));
            }
        }
    }
}
