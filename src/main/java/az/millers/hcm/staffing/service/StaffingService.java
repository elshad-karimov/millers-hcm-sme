package az.millers.hcm.staffing.service;
import az.millers.hcm.common.tenant.TenantContext;

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
import az.millers.hcm.common.BusinessNumbers;

@Service
public class StaffingService {

    private static final String MODULE = "STAFFING";
    private static final String ENTITY = "Position";

    private final PositionRepository repository;
    private final AuditService audit;
    private final CurrentRequest currentRequest;
    private final PositionLifecycleService lifecycle;

    public StaffingService(PositionRepository repository,
                           AuditService audit,
                           CurrentRequest currentRequest,
                           PositionLifecycleService lifecycle) {
        this.repository = repository;
        this.audit = audit;
        this.currentRequest = currentRequest;
        this.lifecycle = lifecycle;
    }

    // ---------- Queries ----------

    @Transactional(readOnly = true)
    public Page<Position> list(String search, UUID orgUnitId, VacancyState vacancyState,
                                PositionStatus status, Pageable pageable) {
        if (StringUtils.hasText(search)) {
            return repository.searchByTitleOrCode(TenantContext.current(), search, pageable);
        }
        if (orgUnitId != null) return repository.findByTenantIdAndOrgUnitId(TenantContext.current(), orgUnitId, pageable);
        if (vacancyState != null) return repository.findByTenantIdAndVacancyState(TenantContext.current(), vacancyState, pageable);
        if (status != null) return repository.findByTenantIdAndStatus(TenantContext.current(), status, pageable);
        return repository.findByTenantId(TenantContext.current(), pageable);
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
        p.setCode(BusinessNumbers.format("POS", 5, repository.nextPositionCodeSequence()));
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

    /**
     * Delegates to {@link PositionLifecycleService#close} so there is exactly
     * one path that closes a position (validation, breadcrumb update, event
     * journal write, audit). This method is kept as a back-compat shim for
     * the older M109 / staffing callers; new code should call the lifecycle
     * service directly via {@code POST /api/positions/{id}/lifecycle/close}.
     */
    @Transactional
    public Position close(UUID id, String reason) {
        return lifecycle.close(id, reason);
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
        // M146 / §8 — position hierarchy; guard against self-reference.
        if (req.parentPositionId() != null && req.parentPositionId().equals(p.getId())) {
            throw new BadRequestException("A position cannot be its own parent");
        }
        p.setParentPositionId(req.parentPositionId());
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
        // M254 / §44 — compliance fields. All nullable; null means
        // "not applicable in this deployment".
        p.setEstablishmentNumber(req.establishmentNumber());
        p.setCivilServiceGrade(req.civilServiceGrade());
        p.setUnionCategory(req.unionCategory());
        p.setExemptStatus(req.exemptStatus());
        p.setOccupationalCategory(req.occupationalCategory());
        p.setLaborClassification(req.laborClassification());
        p.setLegalBasisReference(req.legalBasisReference());
        // M256 / §31 — risk & criticality flags. Default boolean
        // fields to false on create; preserve existing values on
        // update when caller omits them (sent as null).
        if (req.criticalFlag() != null) p.setCriticalFlag(req.criticalFlag());
        if (req.keySkillConcentration() != null) p.setKeySkillConcentration(req.keySkillConcentration());
        if (req.successorRequired() != null) p.setSuccessorRequired(req.successorRequired());
        p.setBusinessImpactScore(req.businessImpactScore());
        p.setRiskCategory(req.riskCategory());
    }

    private VacancyState deriveVacancyState(int approved, int occupied) {
        if (approved == 0) return VacancyState.PLANNED;
        if (occupied == 0) return VacancyState.VACANT;
        if (occupied >= approved) return VacancyState.OCCUPIED;
        return VacancyState.PARTIALLY_OCCUPIED;
    }

    private record VacancyStateSnapshot(VacancyState state, String reason) {}
    // ClosureSnapshot moved into PositionLifecycleService.LifecycleSnapshot (M243).
}
