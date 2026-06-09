package az.millers.hcm.staffing.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.staffing.domain.PositionReplacement;
import az.millers.hcm.staffing.domain.ReplacementAction;
import az.millers.hcm.staffing.domain.ReplacementStatus;
import az.millers.hcm.staffing.repo.PositionReplacementRepository;

/**
 * M246 — Replacement workflow (PRD §16).
 *
 * <p>One thin lifecycle:
 * <pre>
 *   DRAFT ──submit──▶ PENDING_APPROVAL ──approve──▶ APPROVED ──complete──▶ COMPLETED
 *     ▲                       │
 *     │                       └──reject──▶ REJECTED
 *     │
 *     └─────────── cancel ─────────────▶ CANCELLED
 * </pre>
 */
@Service
public class PositionReplacementService {

    private static final String MODULE = "STAFFING";
    private static final String ENTITY = "PositionReplacement";

    private final PositionReplacementRepository repo;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public PositionReplacementService(PositionReplacementRepository repo,
                                       AuditService audit,
                                       CurrentRequest currentRequest) {
        this.repo = repo;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    // ── Reads ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PositionReplacement> forPosition(UUID positionId) {
        return repo.findByPositionIdOrderByCreatedAtDesc(positionId);
    }

    @Transactional(readOnly = true)
    public List<PositionReplacement> forEmployee(UUID employeeId) {
        return repo.findByLeavingEmployeeIdOrderByCreatedAtDesc(employeeId);
    }

    @Transactional(readOnly = true)
    public List<PositionReplacement> listByStatus(ReplacementStatus status) {
        return repo.findByStatusOrderByCreatedAtDesc(status);
    }

    @Transactional(readOnly = true)
    public PositionReplacement get(UUID id) {
        return repo.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Replacement not found: " + id));
    }

    // ── Writes ──────────────────────────────────────────────────────────

    @Transactional
    public PositionReplacement create(PositionReplacement input) {
        if (input.getPositionId() == null) throw new BadRequestException("positionId required");
        if (input.getLeavingEmployeeId() == null) throw new BadRequestException("leavingEmployeeId required");
        if (input.getLastWorkingDay() == null) throw new BadRequestException("lastWorkingDay required");
        if (input.getReason() == null || input.getReason().isBlank())
            throw new BadRequestException("reason required");
        if (input.getAction() == null) input.setAction(ReplacementAction.OPEN_RECRUITMENT);

        // INTERNAL_TRANSFER + ACTING need a replacementEmployeeId.
        if ((input.getAction() == ReplacementAction.INTERNAL_TRANSFER
                || input.getAction() == ReplacementAction.ACTING)
                && input.getReplacementEmployeeId() == null) {
            throw new BadRequestException(
                    "replacementEmployeeId is required for action " + input.getAction());
        }

        input.setId(null);
        input.setStatus(ReplacementStatus.DRAFT);
        input.setCreatedBy(currentRequest.username());
        input.setUpdatedBy(currentRequest.username());
        PositionReplacement saved = repo.save(input);

        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, summary(saved));
        return saved;
    }

    @Transactional
    public PositionReplacement update(UUID id, PositionReplacement patch) {
        PositionReplacement existing = get(id);
        if (existing.getStatus() != ReplacementStatus.DRAFT
                && existing.getStatus() != ReplacementStatus.REJECTED) {
            throw new BadRequestException("Can only edit DRAFT or REJECTED replacements");
        }
        if (patch.getReason() != null) existing.setReason(patch.getReason());
        if (patch.getLastWorkingDay() != null) existing.setLastWorkingDay(patch.getLastWorkingDay());
        if (patch.getAction() != null) existing.setAction(patch.getAction());
        existing.setReplacementEmployeeId(patch.getReplacementEmployeeId());
        existing.setReplacementStartDate(patch.getReplacementStartDate());
        existing.setHandoverOverlapDays(patch.getHandoverOverlapDays());
        existing.setVacancyId(patch.getVacancyId());
        existing.setNotes(patch.getNotes());
        existing.setUpdatedBy(currentRequest.username());

        PositionReplacement saved = repo.save(existing);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "UPDATE", null, summary(saved));
        return saved;
    }

    @Transactional
    public PositionReplacement submit(UUID id) {
        return moveTo(id, ReplacementStatus.PENDING_APPROVAL, r -> {
            if (r.getStatus() != ReplacementStatus.DRAFT
                    && r.getStatus() != ReplacementStatus.REJECTED) {
                throw new BadRequestException("Can only submit a DRAFT or REJECTED replacement");
            }
            r.setSubmittedBy(currentRequest.username());
            r.setSubmittedAt(OffsetDateTime.now());
        });
    }

    @Transactional
    public PositionReplacement approve(UUID id) {
        return moveTo(id, ReplacementStatus.APPROVED, r -> {
            if (r.getStatus() != ReplacementStatus.PENDING_APPROVAL) {
                throw new BadRequestException("Can only approve PENDING_APPROVAL");
            }
            r.setApprovedBy(currentRequest.username());
            r.setApprovedAt(OffsetDateTime.now());
        });
    }

    @Transactional
    public PositionReplacement reject(UUID id, String reason) {
        if (reason == null || reason.isBlank()) throw new BadRequestException("Reject reason required");
        return moveTo(id, ReplacementStatus.REJECTED, r -> {
            if (r.getStatus() != ReplacementStatus.PENDING_APPROVAL) {
                throw new BadRequestException("Can only reject PENDING_APPROVAL");
            }
            r.setRejectedBy(currentRequest.username());
            r.setRejectedAt(OffsetDateTime.now());
            r.setRejectReason(reason);
        });
    }

    /**
     * Mark APPROVED → COMPLETED. The caller is responsible for the
     * downstream action (post vacancy, create acting occupancy, freeze
     * position, etc.) — Phase D leaves those as manual steps so we
     * don't fight the existing flows. Phase D.2 can auto-wire them.
     */
    @Transactional
    public PositionReplacement complete(UUID id) {
        return moveTo(id, ReplacementStatus.COMPLETED, r -> {
            if (r.getStatus() != ReplacementStatus.APPROVED) {
                throw new BadRequestException("Can only complete APPROVED");
            }
            r.setCompletedAt(OffsetDateTime.now());
        });
    }

    @Transactional
    public PositionReplacement cancel(UUID id, String reason) {
        return moveTo(id, ReplacementStatus.CANCELLED, r -> {
            if (r.getStatus().isTerminal()) {
                throw new BadRequestException("Cannot cancel a " + r.getStatus() + " replacement");
            }
            r.setCancelledAt(OffsetDateTime.now());
            r.setCancelReason(reason);
        });
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private PositionReplacement moveTo(UUID id,
                                        ReplacementStatus to,
                                        java.util.function.Consumer<PositionReplacement> sideEffects) {
        PositionReplacement r = get(id);
        ReplacementStatus from = r.getStatus();
        sideEffects.accept(r);
        r.setStatus(to);
        r.setUpdatedBy(currentRequest.username());
        PositionReplacement saved = repo.save(r);
        audit.record(MODULE, ENTITY, r.getId().toString(),
                "LIFECYCLE_" + to.name(),
                java.util.Map.of("from", from.name()),
                java.util.Map.of("to", to.name()));
        return saved;
    }

    private java.util.Map<String, Object> summary(PositionReplacement r) {
        return java.util.Map.of(
                "id", r.getId().toString(),
                "positionId", r.getPositionId().toString(),
                "leavingEmployeeId", r.getLeavingEmployeeId().toString(),
                "action", r.getAction().name(),
                "status", r.getStatus().name());
    }
}
