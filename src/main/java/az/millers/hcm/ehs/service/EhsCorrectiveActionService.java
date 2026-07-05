package az.millers.hcm.ehs.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.ehs.domain.CorrectiveAction;
import az.millers.hcm.ehs.domain.CorrectiveActionPriority;
import az.millers.hcm.ehs.domain.CorrectiveActionStatus;
import az.millers.hcm.ehs.repo.CorrectiveActionRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * M451 — EHS corrective action service.
 * Daily 05:50 sweep marks past-due OPEN/IN_PROGRESS actions OVERDUE.
 */
@Service
public class EhsCorrectiveActionService {

    private static final Logger log = LoggerFactory.getLogger(EhsCorrectiveActionService.class);
    private static final String TENANT = "default";
    private static final String MODULE = "ehs";
    private static final String ENTITY = "CorrectiveAction";

    private final CorrectiveActionRepository repo;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public EhsCorrectiveActionService(CorrectiveActionRepository repo,
                                   AuditService audit,
                                   CurrentRequest currentRequest) {
        this.repo = repo;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional
    public CorrectiveAction create(UUID incidentId,
                                    UUID inspectionId,
                                    UUID riskAssessmentId,
                                    String description,
                                    String responsibleUsername,
                                    LocalDate dueDate,
                                    CorrectiveActionPriority priority) {

        CorrectiveAction action = new CorrectiveAction();
        action.setTenantId(TENANT);
        action.setIncidentId(incidentId);
        action.setInspectionId(inspectionId);
        action.setRiskAssessmentId(riskAssessmentId);
        action.setDescription(description);
        action.setResponsibleUsername(responsibleUsername);
        action.setDueDate(dueDate);
        action.setPriority(priority);
        action.setStatus(CorrectiveActionStatus.OPEN);
        action.setCreatedBy(currentRequest.username());
        action.setUpdatedBy(currentRequest.username());

        CorrectiveAction saved = repo.save(action);

        audit.record(MODULE, ENTITY, saved.getId().toString(), "CREATED", null, null);

        return saved;
    }

    @Transactional
    public CorrectiveAction updateStatus(UUID id,
                                          CorrectiveActionStatus newStatus,
                                          UUID evidenceAttachmentId) {

        CorrectiveAction action = get(id);

        CorrectiveActionStatus oldStatus = action.getStatus();

        action.setStatus(newStatus);
        if (newStatus == CorrectiveActionStatus.COMPLETED) {
            action.setClosedAt(OffsetDateTime.now());
        }
        if (evidenceAttachmentId != null) {
            action.setEvidenceAttachmentId(evidenceAttachmentId);
        }

        action.setUpdatedBy(currentRequest.username());
        action.setUpdatedAt(OffsetDateTime.now());

        CorrectiveAction updated = repo.save(action);

        audit.record(MODULE, ENTITY, id.toString(), "STATUS_CHANGE",
                Map.of("status", oldStatus),
                Map.of("status", newStatus));

        return updated;
    }

    @Transactional(readOnly = true)
    public CorrectiveAction get(UUID id) {
        CorrectiveAction action = repo.findByIdAndTenantId(id, TENANT)
                .orElseThrow(() -> new ResourceNotFoundException("Corrective action not found: " + id));

        // Tenant post-check
        if (!TENANT.equals(action.getTenantId())) {
            throw new ResourceNotFoundException("Corrective action not found: " + id);
        }

        return action;
    }

    @Transactional(readOnly = true)
    public List<CorrectiveAction> list(CorrectiveActionStatus statusFilter, String responsibleUsername) {
        if (statusFilter != null) {
            return repo.findByTenantIdAndStatusOrderByDueDateAsc(TENANT, statusFilter);
        } else if (responsibleUsername != null) {
            return repo.findByTenantIdAndResponsibleUsernameOrderByDueDateAsc(TENANT, responsibleUsername);
        } else {
            return repo.findByTenantIdOrderByDueDateAsc(TENANT);
        }
    }

    /**
     * M451 — Daily sweep at 05:50 to mark past-due actions OVERDUE.
     * Idempotent, audited. Mirrors M447 ER pattern at 05:45.
     */
    @Scheduled(cron = "0 50 5 * * *")
    @Transactional
    public void sweepOverdueActions() {
        LocalDate today = LocalDate.now();
        List<CorrectiveAction> overdue = repo.findOverdueActions(TENANT, today);

        if (overdue.isEmpty()) {
            log.debug("EHS corrective action sweep: no overdue actions");
            return;
        }

        log.info("EHS corrective action sweep: marking {} actions OVERDUE", overdue.size());

        for (CorrectiveAction action : overdue) {
            CorrectiveActionStatus oldStatus = action.getStatus();
            action.setStatus(CorrectiveActionStatus.OVERDUE);
            action.setUpdatedBy("system");
            action.setUpdatedAt(OffsetDateTime.now());
            repo.save(action);

            audit.record(MODULE, ENTITY, action.getId().toString(), "STATUS_CHANGE",
                    Map.of("status", oldStatus),
                    Map.of("status", CorrectiveActionStatus.OVERDUE));
        }
    }
}
