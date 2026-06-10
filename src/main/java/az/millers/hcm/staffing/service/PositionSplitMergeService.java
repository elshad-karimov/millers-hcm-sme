package az.millers.hcm.staffing.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.staffing.api.dto.PositionRequest;
import az.millers.hcm.staffing.api.dto.PositionSplitMergeDtos.MergeRequest;
import az.millers.hcm.staffing.api.dto.PositionSplitMergeDtos.MergeResult;
import az.millers.hcm.staffing.api.dto.PositionSplitMergeDtos.NewPositionRow;
import az.millers.hcm.staffing.api.dto.PositionSplitMergeDtos.SplitRequest;
import az.millers.hcm.staffing.api.dto.PositionSplitMergeDtos.SplitResult;
import az.millers.hcm.staffing.api.dto.PositionSplitMergeDtos.SplitTarget;
import az.millers.hcm.staffing.domain.Position;
import az.millers.hcm.staffing.repo.PositionRepository;

/**
 * M273 — PRD §41 Split & Merge.
 *
 * <p><strong>Split:</strong> one source position → N new positions whose
 * approved_headcount sums to source's approved_headcount. Source must
 * have ZERO active occupants — HR must move them out first via the
 * M260 transfer workflow or M270 contract change.
 *
 * <p><strong>Merge:</strong> N source positions → 1 destination (existing
 * or new). Destination's approved_headcount is set to the sum of
 * sources' (preserving the total). Sources are CLOSED. All sources
 * must have ZERO active occupants for the same reason.
 *
 * <p>The "no occupants" rule keeps v1 tight. v2 could ship a workflow
 * that prompts the operator to map each occupant to a new/destination
 * position before applying — but that's a larger UX scope.
 */
@Service
public class PositionSplitMergeService {

    private static final String MODULE = "STAFFING";
    private static final String ENTITY = "Position";

    private final PositionRepository positions;
    private final StaffingService staffingService;
    private final PositionLifecycleService lifecycleService;
    private final EmployeeRepository employees;
    private final AuditService audit;

    public PositionSplitMergeService(PositionRepository positions,
                                      StaffingService staffingService,
                                      PositionLifecycleService lifecycleService,
                                      EmployeeRepository employees,
                                      AuditService audit) {
        this.positions = positions;
        this.staffingService = staffingService;
        this.lifecycleService = lifecycleService;
        this.employees = employees;
        this.audit = audit;
    }

    // ── Split ──────────────────────────────────────────────────────

    @Transactional
    public SplitResult split(UUID sourceId, SplitRequest req) {
        Position source = positions.findById(sourceId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Source position not found: " + sourceId));
        if (req.targets() == null || req.targets().isEmpty()) {
            throw new BadRequestException("Split requires at least one target");
        }

        long occupied = employees.countActiveByPositionId(sourceId);
        if (occupied > 0) {
            throw new BadRequestException(
                    "Source position " + source.getCode() + " has " + occupied
                    + " active occupant(s). Move them out first via Position Transfer "
                    + "(M260) or Contract Change (M270) before splitting.");
        }

        // Headcount conservation: sum of target headcounts must equal source.
        int sumTargets = req.targets().stream().mapToInt(SplitTarget::approvedHeadcount).sum();
        if (sumTargets != source.getApprovedHeadcount()) {
            throw new BadRequestException(
                    "Target headcounts (" + sumTargets + ") must equal source approved headcount ("
                    + source.getApprovedHeadcount() + ")");
        }

        // Create each new position via StaffingService.create so all the
        // normal bookkeeping fires (audit, lifecycle defaults, M244
        // funding row init, vacancy state derivation, code generation).
        List<NewPositionRow> created = new ArrayList<>();
        for (SplitTarget t : req.targets()) {
            PositionRequest pr = new PositionRequest(
                    t.title(),
                    source.getParentPositionId(),
                    source.getOrgUnitId(),
                    source.getOrgUnitLabel(),
                    t.grade(), t.jobFamily(), t.jobLevel(),
                    t.approvedHeadcount(),
                    t.salaryMin(), t.salaryMax(),
                    source.getCurrency(),
                    source.getEmploymentType(),
                    source.getCostCentre(),
                    source.getBudgetCode(),
                    source.getLocation(),
                    null, null,   // effectiveFrom / effectiveTo
                    // M254 — inherit compliance fields from source so the
                    // split children stay aligned by default; HR can adjust.
                    source.getEstablishmentNumber(),
                    source.getCivilServiceGrade(),
                    source.getUnionCategory(),
                    source.getExemptStatus(),
                    source.getOccupationalCategory(),
                    source.getLaborClassification(),
                    source.getLegalBasisReference(),
                    // M256 risk & criticality — also inherit; HR can re-rank
                    source.isCriticalFlag(),
                    source.getBusinessImpactScore(),
                    source.getRiskCategory(),
                    source.isKeySkillConcentration(),
                    source.isSuccessorRequired());
            Position np = staffingService.create(pr);
            created.add(new NewPositionRow(
                    np.getId(), np.getCode(), np.getTitle(), np.getApprovedHeadcount()));
        }

        // Close the source so it doesn't linger as a phantom seat.
        // M243 close fires the lifecycle audit + breadcrumb columns.
        lifecycleService.close(source.getId(),
                "Split into " + created.size() + " positions"
                        + (req.reason() == null ? "" : " — " + req.reason()));

        audit.record(MODULE, ENTITY, sourceId.toString(),
                "SPLIT",
                java.util.Map.of("sourceCode", source.getCode(),
                                 "sourceHeadcount", source.getApprovedHeadcount()),
                java.util.Map.of("createdCount", created.size(),
                                 "createdCodes",
                                 created.stream().map(NewPositionRow::code).toList()));

        return new SplitResult(sourceId, source.getCode(), created);
    }

    // ── Merge ──────────────────────────────────────────────────────

    @Transactional
    public MergeResult merge(MergeRequest req) {
        if (req.sourcePositionIds() == null || req.sourcePositionIds().isEmpty()) {
            throw new BadRequestException("Merge requires at least one source position");
        }
        if (req.destinationPositionId() == null && req.newDestination() == null) {
            throw new BadRequestException("Provide either destinationPositionId or newDestination");
        }
        if (req.destinationPositionId() != null && req.newDestination() != null) {
            throw new BadRequestException(
                    "Provide ONE of destinationPositionId or newDestination, not both");
        }

        // Load + validate every source.
        List<Position> sources = new ArrayList<>(req.sourcePositionIds().size());
        int totalHeadcount = 0;
        for (UUID sid : req.sourcePositionIds()) {
            Position s = positions.findById(sid)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Source position not found: " + sid));
            long occupied = employees.countActiveByPositionId(sid);
            if (occupied > 0) {
                throw new BadRequestException(
                        "Source " + s.getCode() + " has " + occupied
                        + " active occupant(s). Move them out first.");
            }
            sources.add(s);
            totalHeadcount += s.getApprovedHeadcount();
        }

        // Resolve destination.
        Position destination;
        if (req.destinationPositionId() != null) {
            destination = positions.findById(req.destinationPositionId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Destination position not found: " + req.destinationPositionId()));
            // Add merged headcount to existing destination.
            destination.setApprovedHeadcount(
                    destination.getApprovedHeadcount() + totalHeadcount);
            positions.save(destination);
        } else {
            // Create new destination using the first source as a template.
            Position template = sources.get(0);
            var nd = req.newDestination();
            PositionRequest pr = new PositionRequest(
                    nd.title(),
                    template.getParentPositionId(),
                    nd.orgUnitId() != null ? nd.orgUnitId() : template.getOrgUnitId(),
                    nd.orgUnitLabel() != null ? nd.orgUnitLabel() : template.getOrgUnitLabel(),
                    nd.grade() != null ? nd.grade() : template.getGrade(),
                    nd.jobFamily() != null ? nd.jobFamily() : template.getJobFamily(),
                    nd.jobLevel() != null ? nd.jobLevel() : template.getJobLevel(),
                    totalHeadcount,
                    template.getSalaryMin(), template.getSalaryMax(),
                    template.getCurrency(),
                    template.getEmploymentType(),
                    template.getCostCentre(),
                    template.getBudgetCode(),
                    template.getLocation(),
                    null, null,
                    template.getEstablishmentNumber(),
                    template.getCivilServiceGrade(),
                    template.getUnionCategory(),
                    template.getExemptStatus(),
                    template.getOccupationalCategory(),
                    template.getLaborClassification(),
                    template.getLegalBasisReference(),
                    template.isCriticalFlag(),
                    template.getBusinessImpactScore(),
                    template.getRiskCategory(),
                    template.isKeySkillConcentration(),
                    template.isSuccessorRequired());
            destination = staffingService.create(pr);
        }

        // Close every source.
        List<UUID> archivedIds = new ArrayList<>();
        for (Position s : sources) {
            lifecycleService.close(s.getId(),
                    "Merged into " + destination.getCode()
                            + (req.reason() == null ? "" : " — " + req.reason()));
            archivedIds.add(s.getId());
        }

        audit.record(MODULE, ENTITY, destination.getId().toString(),
                "MERGE",
                java.util.Map.of("sourceCodes",
                                 sources.stream().map(Position::getCode).toList(),
                                 "sourceHeadcountSum", totalHeadcount),
                java.util.Map.of("destinationCode", destination.getCode(),
                                 "destinationHeadcount", destination.getApprovedHeadcount()));

        return new MergeResult(
                destination.getId(), destination.getCode(),
                destination.getApprovedHeadcount(), archivedIds);
    }
}
