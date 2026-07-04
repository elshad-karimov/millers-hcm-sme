package az.millers.hcm.performance.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.performance.api.dto.SuccessionGridDtos.Readiness;
import az.millers.hcm.performance.api.dto.SuccessionNominationDtos.CancelRequest;
import az.millers.hcm.performance.api.dto.SuccessionNominationDtos.NominationRequest;
import az.millers.hcm.performance.api.dto.SuccessionNominationDtos.NominationResponse;
import az.millers.hcm.performance.domain.SuccessionNomination;
import az.millers.hcm.performance.repo.SuccessionNominationRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.staffing.domain.Position;
import az.millers.hcm.staffing.repo.PositionRepository;

/**
 * Named-successor nomination service (M103).
 *
 * <p>Complements M92's bucket placement with explicit HR-auditable records.
 * One position can have multiple successors at different readiness tiers;
 * re-nominating an already-nominated (position, nominee) pair is rejected
 * unless the existing nomination has been cancelled first.
 */
@Service
public class SuccessionNominationService {

    private static final String MODULE = "PERFORMANCE";
    private static final String ENTITY = "SuccessionNomination";

    private final SuccessionNominationRepository nominations;
    private final PositionRepository positions;
    private final EmployeeRepository employees;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public SuccessionNominationService(SuccessionNominationRepository nominations,
                                        PositionRepository positions,
                                        EmployeeRepository employees,
                                        AuditService audit,
                                        CurrentRequest currentRequest) {
        this.nominations = nominations;
        this.positions = positions;
        this.employees = employees;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional
    public NominationResponse nominate(UUID positionId, NominationRequest req) {
        if (req.nomineeEmployeeId() == null || req.readinessTier() == null) {
            throw new BadRequestException("nomineeEmployeeId and readinessTier are required");
        }
        // M414 — validate risk/impact enum values
        validateRiskImpact(req.riskOfLoss(), "riskOfLoss");
        validateRiskImpact(req.impactOfLoss(), "impactOfLoss");

        // Guard: partial unique index handles the race; pre-check gives friendly message.
        nominations.findByTenantIdAndPositionIdAndNomineeEmployeeIdAndCancelledAtIsNull(
                "default", positionId, req.nomineeEmployeeId())
                .ifPresent(existing -> {
                    throw new BadRequestException(
                            "An active nomination already exists for this (position, nominee) pair. "
                            + "Cancel it first before re-nominating at a different tier.");
                });
        Position pos = positions.findById(positionId)
                .orElseThrow(() -> new ResourceNotFoundException("Position not found: " + positionId));

        SuccessionNomination n = new SuccessionNomination();
        n.setPositionId(positionId);
        n.setNomineeEmployeeId(req.nomineeEmployeeId());
        n.setReadinessTier(req.readinessTier());
        n.setNotes(req.notes());
        n.setRiskOfLoss(req.riskOfLoss());
        n.setImpactOfLoss(req.impactOfLoss());
        n.setRiskReason(req.riskReason());
        n.setRetentionAction(req.retentionAction());
        n.setNominatedBy(currentRequest.username());
        nominations.save(n);

        audit.record(MODULE, ENTITY, n.getId().toString(), "NOMINATE",
                null,
                Map.of(
                        "positionId", positionId.toString(),
                        "nomineeId", req.nomineeEmployeeId().toString(),
                        "tier", req.readinessTier().name()));
        return toResponse(n, pos);
    }

    @Transactional
    public NominationResponse cancel(UUID nominationId, CancelRequest req) {
        SuccessionNomination n = mustFind(nominationId);
        if (!n.isActive()) {
            throw new BadRequestException("Nomination is already cancelled");
        }
        n.setCancelledAt(OffsetDateTime.now());
        n.setCancellationReason(req == null ? null : req.reason());
        nominations.save(n);
        audit.record(MODULE, ENTITY, nominationId.toString(), "CANCEL",
                null,
                Map.of("reason", req == null || req.reason() == null ? "" : req.reason()));
        Position pos = positions.findById(n.getPositionId()).orElse(null);
        return toResponse(n, pos);
    }

    @Transactional(readOnly = true)
    public List<NominationResponse> forPosition(UUID positionId) {
        Position pos = positions.findById(positionId).orElse(null);
        return nominations
                .findByTenantIdAndPositionIdAndCancelledAtIsNullOrderByCreatedAtDesc("default", positionId).stream()
                .map(n -> toResponse(n, pos))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NominationResponse> forNominee(UUID employeeId) {
        return nominations
                .findByTenantIdAndNomineeEmployeeIdAndCancelledAtIsNullOrderByCreatedAtDesc("default", employeeId).stream()
                .map(n -> {
                    Position pos = positions.findById(n.getPositionId()).orElse(null);
                    return toResponse(n, pos);
                })
                .toList();
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private NominationResponse toResponse(SuccessionNomination n, Position pos) {
        Employee emp = employees.findById(n.getNomineeEmployeeId()).orElse(null);
        String nomineeName = emp == null ? null
                : ((emp.getFirstName() == null ? "" : emp.getFirstName()) + " "
                + (emp.getLastName() == null ? "" : emp.getLastName())).trim();
        return new NominationResponse(
                n.getId(),
                n.getPositionId(),
                pos == null ? null : pos.getCode(),
                pos == null ? null : pos.getTitle(),
                n.getNomineeEmployeeId(),
                nomineeName,
                emp == null ? null : emp.getEmployeeNo(),
                emp == null ? null : emp.getDepartmentName(),
                n.getReadinessTier(),
                n.getNotes(),
                n.getNominatedBy(),
                n.getCreatedAt(),
                n.isActive(),
                n.getRiskOfLoss(),
                n.getImpactOfLoss(),
                n.getRiskReason(),
                n.getRetentionAction());
    }

    /** M414 — validate risk/impact values (LOW|MEDIUM|HIGH|CRITICAL or null) */
    private void validateRiskImpact(String value, String fieldName) {
        if (value != null && !value.matches("^(LOW|MEDIUM|HIGH|CRITICAL)$")) {
            throw new BadRequestException(
                    fieldName + " must be LOW, MEDIUM, HIGH, or CRITICAL (got: " + value + ")");
        }
    }

    private SuccessionNomination mustFind(UUID id) {
        return nominations.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Nomination not found: " + id));
    }
}
