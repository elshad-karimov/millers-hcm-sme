package az.millers.hcm.staffing.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.staffing.domain.FundingStatus;
import az.millers.hcm.staffing.domain.Position;
import az.millers.hcm.staffing.domain.PositionFunding;
import az.millers.hcm.staffing.repo.PositionFundingRepository;
import az.millers.hcm.staffing.repo.PositionRepository;

/**
 * M244 — singleton funding state per position (PRD §21).
 *
 * <p>The position management lifecycle (M243) and the funding axis are
 * independent. A position can be lifecycle-ACTIVE but funding-UNFUNDED —
 * in that case {@link #assertCanRecruit(UUID)} throws so the
 * recruitment + fill paths refuse to enrol new employees.
 */
@Service
public class PositionFundingService {

    private static final String MODULE = "STAFFING";
    private static final String ENTITY = "PositionFunding";

    private final PositionFundingRepository funding;
    private final PositionRepository positions;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public PositionFundingService(PositionFundingRepository funding,
                                   PositionRepository positions,
                                   AuditService audit,
                                   CurrentRequest currentRequest) {
        this.funding = funding;
        this.positions = positions;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public PositionFunding get(UUID positionId) {
        return funding.findById(positionId).orElseGet(() -> {
            // No row yet — return a transient UNFUNDED default so callers
            // never have to handle "no funding row".
            PositionFunding p = new PositionFunding();
            p.setPositionId(positionId);
            p.setStatus(FundingStatus.UNFUNDED);
            return p;
        });
    }

    @Transactional(readOnly = true)
    public List<PositionFunding> listByStatus(FundingStatus status) {
        return funding.findByStatus(status);
    }

    /** All funding rows; used by the SPA list-page batch lookup. */
    @Transactional(readOnly = true)
    public List<PositionFunding> listAll() {
        return funding.findAll();
    }

    /**
     * Upsert funding for a position. Creates the row if missing.
     */
    @Transactional
    public PositionFunding upsert(UUID positionId, PositionFunding input) {
        Position p = positions.findById(positionId).orElseThrow(
                () -> new ResourceNotFoundException("Position not found: " + positionId));

        PositionFunding existing = funding.findById(positionId).orElseGet(() -> {
            PositionFunding f = new PositionFunding();
            f.setPositionId(p.getId());
            return f;
        });

        FundingStatus before = existing.getStatus();
        FundingStatus next = input.getStatus() != null ? input.getStatus() : FundingStatus.UNFUNDED;

        // Auto-expire: if expiry is set in the past, force EXPIRED even if
        // the caller asked for FUNDED.
        if (input.getFundingExpiry() != null
                && input.getFundingExpiry().isBefore(LocalDate.now())
                && next == FundingStatus.FUNDED) {
            next = FundingStatus.EXPIRED;
        }

        existing.setStatus(next);
        existing.setFundingSource(input.getFundingSource());
        existing.setFundingOwner(input.getFundingOwner());
        existing.setFundingExpiry(input.getFundingExpiry());
        existing.setNotes(input.getNotes());
        existing.setUpdatedBy(currentRequest.username());

        PositionFunding saved = funding.save(existing);
        audit.record(MODULE, ENTITY, positionId.toString(),
                "FUNDING_" + next.name(),
                new FundingSnapshot(before),
                new FundingSnapshot(next));
        return saved;
    }

    /**
     * Gate used by {@link PositionHeadcountService#assertCanFill} and by
     * the recruitment vacancy create path. Throws {@link BadRequestException}
     * if the position's funding does not permit new recruits.
     *
     * <p>{@link FundingStatus#allowsRecruitment()} is the single point of
     * truth — FUNDED + PARTIALLY_FUNDED pass; everything else fails. If
     * no funding row exists, treat it as UNFUNDED.
     */
    @Transactional(readOnly = true)
    public void assertCanRecruit(UUID positionId) {
        PositionFunding f = get(positionId);
        if (!f.getStatus().allowsRecruitment()) {
            // Look up the code for a friendly error.
            String code = positions.findById(positionId)
                    .map(Position::getCode)
                    .orElse(positionId.toString());
            throw new BadRequestException(
                    "Position " + code + " is " + f.getStatus()
                    + " — funding required before recruitment / new hires");
        }
    }

    private record FundingSnapshot(FundingStatus status) {}
}
