package az.millers.hcm.staffing.service;

import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.staffing.api.dto.PositionRequest;
import az.millers.hcm.staffing.api.dto.PositionResponse;
import az.millers.hcm.staffing.domain.Position;
import az.millers.hcm.staffing.domain.PositionStatus;
import az.millers.hcm.staffing.domain.VacancyState;
import az.millers.hcm.staffing.repo.PositionRepository;

@Service
public class StaffingService {

    private static final String MODULE = "STAFFING";
    private static final String ENTITY = "Position";

    private final PositionRepository repository;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public StaffingService(PositionRepository repository,
                           AuditService audit,
                           CurrentRequest currentRequest) {
        this.repository = repository;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    // ---------- Queries ----------

    @Transactional(readOnly = true)
    public Page<Position> list(String search, UUID orgUnitId, VacancyState vacancyState,
                                PositionStatus status, Pageable pageable) {
        if (StringUtils.hasText(search)) {
            return repository.findByTitleContainingIgnoreCaseOrCodeContainingIgnoreCase(
                    search, search, pageable);
        }
        if (orgUnitId != null) return repository.findByOrgUnitId(orgUnitId, pageable);
        if (vacancyState != null) return repository.findByVacancyState(vacancyState, pageable);
        if (status != null) return repository.findByStatus(status, pageable);
        return repository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Position get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Position not found: " + id));
    }

    // ---------- Lifecycle ----------

    @Transactional
    public Position create(PositionRequest req) {
        Position p = new Position();
        p.setCode(String.format("POS-%05d", repository.nextPositionCodeSequence()));
        applyRequest(p, req);
        p.setOccupiedHeadcount(0);
        p.setStatus(PositionStatus.ACTIVE);
        p.setVacancyState(deriveVacancyState(p.getApprovedHeadcount(), 0));
        p.setCreatedBy(currentRequest.username());
        p.setUpdatedBy(currentRequest.username());
        Position saved = repository.save(p);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, PositionResponse.from(saved));
        return saved;
    }

    @Transactional
    public Position update(UUID id, PositionRequest req) {
        Position p = get(id);
        if (p.getStatus() == PositionStatus.CLOSED) {
            throw new BadRequestException("Cannot edit a CLOSED position");
        }
        PositionResponse before = PositionResponse.from(p);
        applyRequest(p, req);
        // Only re-derive vacancy state if not explicitly FROZEN.
        if (p.getVacancyState() != VacancyState.FROZEN) {
            p.setVacancyState(deriveVacancyState(p.getApprovedHeadcount(), p.getOccupiedHeadcount()));
        }
        p.setUpdatedBy(currentRequest.username());
        Position saved = repository.save(p);
        audit.record(MODULE, ENTITY, id.toString(),
                "UPDATE", before, PositionResponse.from(saved));
        return saved;
    }

    @Transactional
    public Position changeVacancyState(UUID id, VacancyState newState, String reason) {
        Position p = get(id);
        if (p.getVacancyState() == newState) {
            throw new BadRequestException("Position is already in state " + newState);
        }
        VacancyState old = p.getVacancyState();
        p.setVacancyState(newState);
        p.setUpdatedBy(currentRequest.username());
        Position saved = repository.save(p);
        audit.record(MODULE, ENTITY, id.toString(), "VACANCY_STATE_CHANGE",
                new VacancyStateSnapshot(old, null),
                new VacancyStateSnapshot(newState, reason));
        return saved;
    }

    @Transactional
    public Position close(UUID id, String reason) {
        Position p = get(id);
        if (p.getStatus() == PositionStatus.CLOSED) {
            throw new BadRequestException("Position is already closed");
        }
        if (p.getOccupiedHeadcount() > 0) {
            throw new BadRequestException("Cannot close a position with occupied headcount");
        }
        PositionResponse before = PositionResponse.from(p);
        p.setStatus(PositionStatus.CLOSED);
        p.setVacancyState(VacancyState.CANCELLED);
        p.setUpdatedBy(currentRequest.username());
        Position saved = repository.save(p);
        audit.record(MODULE, ENTITY, id.toString(),
                "CLOSE", before,
                new ClosureSnapshot(PositionStatus.CLOSED, reason));
        return saved;
    }

    /**
     * Applies a delta to the position's occupied headcount and re-derives the
     * vacancy state. Used by Recruitment on HIRE (+1) and Termination on exit (−1).
     */
    @Transactional
    public Position adjustOccupancy(UUID id, int delta, String reason) {
        Position p = get(id);
        int next = p.getOccupiedHeadcount() + delta;
        if (next < 0) {
            throw new BadRequestException("Occupied headcount cannot go below zero");
        }
        if (next > p.getApprovedHeadcount()) {
            throw new BadRequestException(
                    "Occupied (" + next + ") would exceed approved headcount ("
                            + p.getApprovedHeadcount() + ")");
        }
        PositionResponse before = PositionResponse.from(p);
        p.setOccupiedHeadcount(next);
        if (p.getVacancyState() != VacancyState.FROZEN) {
            p.setVacancyState(deriveVacancyState(p.getApprovedHeadcount(), next));
        }
        p.setUpdatedBy(currentRequest.username());
        Position saved = repository.save(p);
        audit.record(MODULE, ENTITY, id.toString(),
                "ADJUST_OCCUPANCY", before,
                Map.of("delta", delta, "newOccupied", next,
                        "reason", reason == null ? "" : reason));
        return saved;
    }

    // ---------- Internals ----------

    private void applyRequest(Position p, PositionRequest req) {
        p.setTitle(req.title());
        p.setOrgUnitId(req.orgUnitId());
        p.setOrgUnitLabel(req.orgUnitLabel());
        p.setGrade(req.grade());
        p.setJobFamily(req.jobFamily());
        p.setJobLevel(req.jobLevel());
        p.setApprovedHeadcount(req.approvedHeadcount() == null ? 0 : req.approvedHeadcount());
        if (req.salaryMin() != null && req.salaryMax() != null
                && req.salaryMin().compareTo(req.salaryMax()) > 0) {
            throw new BadRequestException("salaryMin cannot be greater than salaryMax");
        }
        p.setSalaryMin(req.salaryMin());
        p.setSalaryMax(req.salaryMax());
        p.setCurrency(StringUtils.hasText(req.currency()) ? req.currency().toUpperCase() : "AZN");
        p.setEmploymentType(req.employmentType());
        p.setCostCentre(req.costCentre());
        p.setBudgetCode(req.budgetCode());
        p.setLocation(req.location());
        p.setEffectiveFrom(req.effectiveFrom());
        p.setEffectiveTo(req.effectiveTo());
    }

    private VacancyState deriveVacancyState(int approved, int occupied) {
        if (approved == 0) return VacancyState.PLANNED;
        if (occupied == 0) return VacancyState.VACANT;
        if (occupied >= approved) return VacancyState.OCCUPIED;
        return VacancyState.PARTIALLY_OCCUPIED;
    }

    private record VacancyStateSnapshot(VacancyState state, String reason) {}
    private record ClosureSnapshot(PositionStatus status, String reason) {}
}
