package az.millers.hcm.staffing.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.organization.repo.OrgUnitRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.staffing.api.dto.PositionTransferDtos.ActionRequest;
import az.millers.hcm.staffing.api.dto.PositionTransferDtos.InitiateRequest;
import az.millers.hcm.staffing.api.dto.PositionTransferDtos.TransferResponse;
import az.millers.hcm.staffing.domain.Position;
import az.millers.hcm.staffing.domain.PositionTransfer;
import az.millers.hcm.staffing.domain.TransferStatus;
import az.millers.hcm.staffing.repo.PositionRepository;
import az.millers.hcm.staffing.repo.PositionTransferRepository;

/**
 * M260 — Position transfer workflow (PRD §40).
 *
 * <p>State machine:
 * <pre>
 *   DRAFT  →  PENDING_APPROVAL  →  APPROVED  →  COMPLETED
 *                                 ↘
 *                                  REJECTED
 *   any non-terminal  →  CANCELLED
 * </pre>
 *
 * <p>On {@link #complete}, the new org unit / cost centre / location is
 * applied to {@link Position} so the position record reflects its new
 * home. The transfer row stays as the audit trail; the M243 lifecycle
 * event journal records the change separately via the staffing audit.
 */
@Service
public class PositionTransferService {

    private static final String MODULE = "STAFFING";
    private static final String ENTITY = "PositionTransfer";

    private static final List<TransferStatus> IN_FLIGHT = List.of(
            TransferStatus.DRAFT,
            TransferStatus.PENDING_APPROVAL,
            TransferStatus.APPROVED);

    private final PositionTransferRepository transfers;
    private final PositionRepository positions;
    private final OrgUnitRepository orgUnits;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public PositionTransferService(PositionTransferRepository transfers,
                                    PositionRepository positions,
                                    OrgUnitRepository orgUnits,
                                    AuditService audit,
                                    CurrentRequest currentRequest) {
        this.transfers = transfers;
        this.positions = positions;
        this.orgUnits = orgUnits;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    // ── Reads ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TransferResponse> listForPosition(UUID positionId) {
        return transfers.findByPositionIdOrderByCreatedAtDesc(positionId)
                .stream().map(TransferResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TransferResponse get(UUID transferId) {
        return TransferResponse.from(find(transferId));
    }

    // ── Initiate ─────────────────────────────────────────────────────

    @Transactional
    public TransferResponse initiate(InitiateRequest req) {
        Position p = positions.findById(req.positionId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Position not found: " + req.positionId()));

        // Gate against multiple in-flight transfers on the same position.
        if (!transfers.findByPositionIdAndStatusIn(p.getId(), IN_FLIGHT).isEmpty()) {
            throw new BadRequestException(
                    "Position already has an in-flight transfer — cancel it first.");
        }
        // Either target org unit OR target cost centre OR target location
        // must differ from the source. Otherwise the transfer is a no-op.
        boolean sameOrg = sameNullableUuid(p.getOrgUnitId(), req.toOrgUnitId());
        boolean sameCC  = sameNullableString(p.getCostCentre(), req.toCostCentre());
        boolean sameLoc = sameNullableString(p.getLocation(), req.toLocation());
        if (sameOrg && sameCC && sameLoc) {
            throw new BadRequestException(
                    "Transfer is a no-op — at least one target field must differ.");
        }

        PositionTransfer t = new PositionTransfer();
        t.setPositionId(p.getId());

        // Snapshot FROM side.
        t.setFromOrgUnitId(p.getOrgUnitId());
        t.setFromOrgUnitLabel(p.getOrgUnitLabel());
        t.setFromCostCentre(p.getCostCentre());
        t.setFromLocation(p.getLocation());

        // TO side. If only org-unit-id is given, resolve its name into
        // the label snapshot so the SPA can show "moving to Engineering"
        // without a second fetch.
        t.setToOrgUnitId(req.toOrgUnitId());
        if (req.toOrgUnitId() != null) {
            orgUnits.findById(req.toOrgUnitId())
                    .ifPresent(u -> t.setToOrgUnitLabel(u.getName()));
        }
        t.setToCostCentre(req.toCostCentre());
        t.setToLocation(req.toLocation());

        t.setTransferReason(req.transferReason());
        t.setNotes(req.notes());
        t.setEffectiveDate(req.effectiveDate());

        t.setStatus(TransferStatus.DRAFT);
        t.setRequestedBy(currentRequest.username());
        t.setRequestedAt(OffsetDateTime.now());

        PositionTransfer saved = transfers.save(t);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "INITIATE", null, TransferResponse.from(saved));
        return TransferResponse.from(saved);
    }

    // ── State transitions ───────────────────────────────────────────

    @Transactional
    public TransferResponse submit(UUID id) {
        return transition(id, TransferStatus.DRAFT, TransferStatus.PENDING_APPROVAL,
                "SUBMIT", t -> {
                    t.setSubmittedBy(currentRequest.username());
                    t.setSubmittedAt(OffsetDateTime.now());
                });
    }

    @Transactional
    public TransferResponse approve(UUID id) {
        return transition(id, TransferStatus.PENDING_APPROVAL, TransferStatus.APPROVED,
                "APPROVE", t -> {
                    t.setApprovedBy(currentRequest.username());
                    t.setApprovedAt(OffsetDateTime.now());
                });
    }

    @Transactional
    public TransferResponse reject(UUID id, ActionRequest req) {
        return transition(id, TransferStatus.PENDING_APPROVAL, TransferStatus.REJECTED,
                "REJECT", t -> {
                    t.setRejectedBy(currentRequest.username());
                    t.setRejectedAt(OffsetDateTime.now());
                    t.setRejectReason(req == null ? null : req.reason());
                });
    }

    /**
     * Apply the transfer to the position. Hard step — the position record
     * is mutated to the new org unit / cost centre / location.
     */
    @Transactional
    public TransferResponse complete(UUID id) {
        PositionTransfer t = find(id);
        if (t.getStatus() != TransferStatus.APPROVED) {
            throw new BadRequestException(
                    "Transfer must be APPROVED to complete (was " + t.getStatus() + ")");
        }
        // Mutate the position.
        Position p = positions.findById(t.getPositionId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Position vanished: " + t.getPositionId()));
        // Only apply fields that were actually set on the transfer — null
        // means "leave alone".
        if (t.getToOrgUnitId() != null) {
            p.setOrgUnitId(t.getToOrgUnitId());
            p.setOrgUnitLabel(t.getToOrgUnitLabel());
        }
        if (t.getToCostCentre() != null) p.setCostCentre(t.getToCostCentre());
        if (t.getToLocation() != null) p.setLocation(t.getToLocation());
        positions.save(p);

        // Mark transfer complete.
        TransferResponse before = TransferResponse.from(t);
        t.setStatus(TransferStatus.COMPLETED);
        t.setCompletedBy(currentRequest.username());
        t.setCompletedAt(OffsetDateTime.now());
        PositionTransfer saved = transfers.save(t);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "COMPLETE", before, TransferResponse.from(saved));
        return TransferResponse.from(saved);
    }

    @Transactional
    public TransferResponse cancel(UUID id, ActionRequest req) {
        PositionTransfer t = find(id);
        if (t.getStatus().isTerminal()) {
            throw new BadRequestException(
                    "Cannot cancel a terminal transfer (status " + t.getStatus() + ")");
        }
        TransferResponse before = TransferResponse.from(t);
        t.setStatus(TransferStatus.CANCELLED);
        t.setCancelledBy(currentRequest.username());
        t.setCancelledAt(OffsetDateTime.now());
        t.setCancelReason(req == null ? null : req.reason());
        PositionTransfer saved = transfers.save(t);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CANCEL", before, TransferResponse.from(saved));
        return TransferResponse.from(saved);
    }

    // ── Helpers ─────────────────────────────────────────────────────

    @FunctionalInterface
    private interface Mutator {
        void apply(PositionTransfer t);
    }

    private TransferResponse transition(UUID id,
                                         TransferStatus expected,
                                         TransferStatus target,
                                         String action,
                                         Mutator mutator) {
        PositionTransfer t = find(id);
        if (t.getStatus() != expected) {
            throw new BadRequestException(
                    "Cannot " + action + " from " + t.getStatus() + " (expected " + expected + ")");
        }
        TransferResponse before = TransferResponse.from(t);
        t.setStatus(target);
        mutator.apply(t);
        PositionTransfer saved = transfers.save(t);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                action, before, TransferResponse.from(saved));
        return TransferResponse.from(saved);
    }

    private PositionTransfer find(UUID id) {
        return transfers.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Transfer not found: " + id));
    }

    private static boolean sameNullableUuid(UUID a, UUID b) {
        return a == null ? b == null : a.equals(b);
    }

    private static boolean sameNullableString(String a, String b) {
        if (a == null || a.isBlank()) return b == null || b.isBlank();
        return a.equals(b);
    }
}
