package az.millers.hcm.staffing.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.staffing.api.dto.HeadcountChangeDtos.HeadcountChangeSubmitRequest;
import az.millers.hcm.staffing.domain.HeadcountChangeRequest;
import az.millers.hcm.staffing.domain.Position;
import az.millers.hcm.staffing.repo.HeadcountChangeRequestRepository;
import az.millers.hcm.staffing.repo.PositionRepository;
import az.millers.hcm.workflow.api.dto.StartWorkflowRequest;
import az.millers.hcm.workflow.domain.WorkflowInstance;
import az.millers.hcm.workflow.event.WorkflowCompletedEvent;
import az.millers.hcm.workflow.service.WorkflowService;

/**
 * Handles the headcount-change-request lifecycle (M156 / §8.3.7).
 *
 * <ol>
 *   <li>HR / Dept Head submits a delta via {@link #submit}.</li>
 *   <li>The request is routed through workflow {@code HEADCOUNT_CHANGE_REQUEST}.</li>
 *   <li>On APPROVED, {@link #onApproved} increments position's
 *       {@code approvedHeadcount} by the delta and lets the headcount
 *       reconciliation scheduler open the corresponding vacancies.</li>
 * </ol>
 */
@Service
public class HeadcountChangeRequestService {

    private static final Logger log =
            LoggerFactory.getLogger(HeadcountChangeRequestService.class);

    public static final String WORKFLOW_DEFINITION = "HEADCOUNT_CHANGE_REQUEST";
    private static final String MODULE  = "STAFFING";
    private static final String ENTITY  = "HeadcountChangeRequest";

    private final HeadcountChangeRequestRepository repo;
    private final PositionRepository positions;
    private final WorkflowService workflowService;
    private final PositionHeadcountService headcountService;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public HeadcountChangeRequestService(HeadcountChangeRequestRepository repo,
                                          PositionRepository positions,
                                          WorkflowService workflowService,
                                          PositionHeadcountService headcountService,
                                          AuditService audit,
                                          CurrentRequest currentRequest) {
        this.repo             = repo;
        this.positions        = positions;
        this.workflowService  = workflowService;
        this.headcountService = headcountService;
        this.audit            = audit;
        this.currentRequest   = currentRequest;
    }

    // ── Public API ─────────────────────────────────────────────────────────

    public List<HeadcountChangeRequest> listByPosition(UUID positionId) {
        return repo.findByPositionIdOrderByCreatedAtDesc(positionId);
    }

    public List<HeadcountChangeRequest> listPending() {
        return repo.findByStatusOrderByCreatedAtDesc("PENDING");
    }

    @Transactional
    public HeadcountChangeRequest submit(UUID positionId,
                                          HeadcountChangeSubmitRequest req) {
        Position p = positions.findById(positionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Position not found: " + positionId));

        if (req.requestedDelta() == 0) {
            throw new BadRequestException("requestedDelta must be non-zero");
        }
        // Guard: a decrease cannot push approved below occupied.
        if (req.requestedDelta() < 0) {
            int newApproved = p.getApprovedHeadcount() + req.requestedDelta();
            if (newApproved < p.getOccupiedHeadcount()) {
                throw new BadRequestException(
                        "Delta would reduce approved headcount below occupied ("
                                + p.getOccupiedHeadcount() + ")");
            }
        }

        HeadcountChangeRequest hcr = new HeadcountChangeRequest();
        hcr.setPositionId(positionId);
        hcr.setRequestedDelta(req.requestedDelta());
        hcr.setReason(req.reason());
        hcr.setStatus("PENDING");
        hcr.setRequestedBy(currentRequest.username());
        hcr = repo.save(hcr);

        String title = p.getCode() + " — " + p.getTitle()
                + " (" + (req.requestedDelta() > 0 ? "+" : "")
                + req.requestedDelta() + " headcount)";

        WorkflowInstance instance = workflowService.start(new StartWorkflowRequest(
                WORKFLOW_DEFINITION,
                MODULE,
                ENTITY,
                hcr.getId().toString(),
                title,
                Map.of(
                        "positionCode", p.getCode(),
                        "positionTitle", p.getTitle(),
                        "delta", req.requestedDelta(),
                        "currentApproved", p.getApprovedHeadcount(),
                        "requestedBy", hcr.getRequestedBy())));

        hcr.setWorkflowInstanceId(instance.getId());
        hcr = repo.save(hcr);

        audit.record(MODULE, ENTITY, hcr.getId().toString(), "SUBMIT",
                null,
                Map.of("positionId", positionId, "delta", req.requestedDelta()));
        return hcr;
    }

    // ── WorkflowCompletedEvent listener ────────────────────────────────────

    @EventListener
    @Transactional
    public void onWorkflowCompleted(WorkflowCompletedEvent event) {
        if (!WORKFLOW_DEFINITION.equals(event.definitionCode())) return;
        if (!ENTITY.equals(event.subjectEntity())) return;

        UUID hcrId;
        try {
            hcrId = UUID.fromString(event.subjectId());
        } catch (IllegalArgumentException e) {
            log.warn("HeadcountChangeRequest: invalid subjectId '{}'", event.subjectId());
            return;
        }

        HeadcountChangeRequest hcr = repo.findById(hcrId)
                .orElseGet(() -> {
                    log.warn("HeadcountChangeRequest {} not found for workflow {}",
                            hcrId, event.instanceId());
                    return null;
                });
        if (hcr == null) return;

        switch (event.status()) {
            case APPROVED, AUTO_APPROVED -> onApproved(hcr, event.actor());
            case REJECTED, RETURNED      -> onRejected(hcr, event.actor(), event.comment());
            case CANCELLED               -> onCancelled(hcr);
            default -> log.warn("Unexpected terminal status {} for HCR workflow {}",
                    event.status(), event.instanceId());
        }
    }

    // ── Internal outcome methods ────────────────────────────────────────────

    private void onApproved(HeadcountChangeRequest hcr, String actor) {
        if (!"PENDING".equals(hcr.getStatus())) return; // idempotent guard

        Position p = positions.findById(hcr.getPositionId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Position not found: " + hcr.getPositionId()));

        int oldApproved = p.getApprovedHeadcount();
        int newApproved = Math.max(0, oldApproved + hcr.getRequestedDelta());
        p.setApprovedHeadcount(newApproved);
        positions.save(p);

        // Trigger headcount reconciliation so vacant slots are opened/closed
        // correctly — the reconciler already handles the vacancyState logic.
        headcountService.reconcile();

        hcr.setStatus("APPROVED");
        hcr.setApprovedBy(actor);
        hcr.setApprovedAt(OffsetDateTime.now());
        repo.save(hcr);

        audit.record(MODULE, ENTITY, hcr.getId().toString(), "APPROVED",
                Map.of("oldApproved", oldApproved),
                Map.of("newApproved", newApproved, "approvedBy", actor));
        log.info("HeadcountChangeRequest {} approved by {}; position {} "
                + "approved headcount {} → {}",
                hcr.getId(), actor, p.getCode(), oldApproved, newApproved);
    }

    private void onRejected(HeadcountChangeRequest hcr, String actor, String reason) {
        if (!"PENDING".equals(hcr.getStatus())) return;
        hcr.setStatus("REJECTED");
        hcr.setRejectedBy(actor);
        hcr.setRejectedAt(OffsetDateTime.now());
        hcr.setRejectReason(reason);
        repo.save(hcr);
        audit.record(MODULE, ENTITY, hcr.getId().toString(), "REJECTED",
                null, Map.of("rejectedBy", actor));
    }

    private void onCancelled(HeadcountChangeRequest hcr) {
        if (!"PENDING".equals(hcr.getStatus())) return;
        hcr.setStatus("WITHDRAWN");
        repo.save(hcr);
    }
}
