package az.millers.hcm.compbenefits.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.compbenefits.api.dto.CompPlanningDtos.CycleRequest;
import az.millers.hcm.compbenefits.api.dto.CompPlanningDtos.CycleResponse;
import az.millers.hcm.compbenefits.api.dto.CompPlanningDtos.DecisionRequest;
import az.millers.hcm.compbenefits.api.dto.CompPlanningDtos.ProposalRequest;
import az.millers.hcm.compbenefits.api.dto.CompPlanningDtos.ProposalResponse;
import az.millers.hcm.compbenefits.api.dto.CompPlanningDtos.TeamGrid;
import az.millers.hcm.compbenefits.api.dto.CompPlanningDtos.TeamGrid.TeamRow;
import az.millers.hcm.compbenefits.domain.CompCycle;
import az.millers.hcm.compbenefits.domain.CompCycleStatus;
import az.millers.hcm.compbenefits.domain.CompProposal;
import az.millers.hcm.compbenefits.domain.CompProposalStatus;
import az.millers.hcm.compbenefits.repo.CompCycleRepository;
import az.millers.hcm.compbenefits.repo.CompProposalRepository;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.payroll.domain.EmployeeCompensation;
import az.millers.hcm.payroll.service.CompensationService;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.scope.AccessScopeService;

/**
 * Compensation planning workbench (M118 Phase 1).
 *
 * <p>The orchestration ties three existing modules together:
 * <ul>
 *   <li><b>Core HR</b> for direct reports + employee names.</li>
 *   <li><b>Payroll {@code CompensationService}</b> for the current
 *       salary (effective-dated) and the write-through on approval.</li>
 *   <li><b>{@code CompPlanningMath}</b> for the percentage / budget
 *       math (pinned by unit tests).</li>
 * </ul>
 *
 * <p>Approving a proposal calls {@link CompensationService#upsert} so the
 * change flows through the standard effective-dated machinery — payroll
 * runs after the {@code effectiveOn} date pick the new salary
 * automatically.
 */
@Service
public class CompPlanningService {

    private static final String MODULE = "COMP_BENEFITS";
    private static final String CYCLE_ENTITY = "CompCycle";
    private static final String PROPOSAL_ENTITY = "CompProposal";

    private final CompCycleRepository cycles;
    private final CompProposalRepository proposals;
    private final EmployeeRepository employees;
    private final CompensationService compensations;
    private final AccessScopeService accessScope;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public CompPlanningService(CompCycleRepository cycles,
                                CompProposalRepository proposals,
                                EmployeeRepository employees,
                                CompensationService compensations,
                                AccessScopeService accessScope,
                                AuditService audit,
                                CurrentRequest currentRequest) {
        this.cycles = cycles;
        this.proposals = proposals;
        this.employees = employees;
        this.compensations = compensations;
        this.accessScope = accessScope;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    // ─── Cycles ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CycleResponse> listCycles(CompCycleStatus status) {
        List<CompCycle> rows = status == null
                ? cycles.findAllByOrderByCreatedAtDesc()
                : cycles.findByStatusOrderByCreatedAtDesc(status);
        return rows.stream().map(this::decorateCycle).toList();
    }

    @Transactional(readOnly = true)
    public CycleResponse getCycle(UUID id) {
        return decorateCycle(loadCycle(id));
    }

    @Transactional
    public CycleResponse createCycle(CycleRequest req) {
        validateCycle(req);
        if (cycles.existsByCode(req.code())) {
            throw new BadRequestException("Cycle code already exists: " + req.code());
        }
        CompCycle c = new CompCycle();
        applyCycle(c, req);
        c.setStatus(CompCycleStatus.DRAFT);
        c.setCreatedBy(currentRequest.username());
        CompCycle saved = cycles.save(c);
        CycleResponse response = decorateCycle(saved);
        audit.record(MODULE, CYCLE_ENTITY, saved.getId().toString(),
                "CREATE", null, response);
        return response;
    }

    @Transactional
    public CycleResponse updateCycle(UUID id, CycleRequest req) {
        validateCycle(req);
        CompCycle c = loadCycle(id);
        if (c.getStatus() == CompCycleStatus.CLOSED || c.getStatus() == CompCycleStatus.CANCELLED) {
            throw new BadRequestException("Cannot edit a " + c.getStatus() + " cycle");
        }
        if (!c.getCode().equals(req.code()) && cycles.existsByCode(req.code())) {
            throw new BadRequestException("Cycle code already exists: " + req.code());
        }
        CycleResponse before = decorateCycle(c);
        applyCycle(c, req);
        CompCycle saved = cycles.save(c);
        CycleResponse response = decorateCycle(saved);
        audit.record(MODULE, CYCLE_ENTITY, id.toString(), "UPDATE", before, response);
        return response;
    }

    @Transactional
    public CycleResponse changeCycleStatus(UUID id, CompCycleStatus newStatus) {
        CompCycle c = loadCycle(id);
        if (c.getStatus() == newStatus) {
            throw new BadRequestException("Cycle is already " + newStatus);
        }
        validateCycleTransition(c.getStatus(), newStatus);
        CycleResponse before = decorateCycle(c);
        c.setStatus(newStatus);
        CompCycle saved = cycles.save(c);
        CycleResponse response = decorateCycle(saved);
        audit.record(MODULE, CYCLE_ENTITY, id.toString(),
                "STATUS_CHANGE", before, response);
        return response;
    }

    private void applyCycle(CompCycle c, CycleRequest req) {
        c.setCode(req.code());
        c.setName(req.name());
        c.setDescription(req.description());
        c.setOpensOn(req.opensOn());
        c.setClosesOn(req.closesOn());
        c.setPoolTotal(req.poolTotal() == null ? BigDecimal.ZERO : req.poolTotal());
        c.setCurrency(req.currency() == null || req.currency().isBlank()
                ? "AZN" : req.currency().toUpperCase());
    }

    private CompCycle loadCycle(UUID id) {
        return cycles.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Comp cycle not found: " + id));
    }

    private CycleResponse decorateCycle(CompCycle c) {
        BigDecimal committed = proposals.committedDelta(c.getId());
        if (committed == null) committed = BigDecimal.ZERO;
        int count = proposals.findByCycleIdOrderByProposedAtDesc(c.getId()).size();
        return CycleResponse.from(c, committed, count);
    }

    /** Package-private — pinned by the unit test. */
    static void validateCycle(CycleRequest req) {
        if (req.opensOn() == null || req.closesOn() == null) {
            throw new BadRequestException("opensOn and closesOn are required");
        }
        if (req.closesOn().isBefore(req.opensOn())) {
            throw new BadRequestException("closesOn must be on or after opensOn");
        }
        if (req.poolTotal() != null && req.poolTotal().signum() < 0) {
            throw new BadRequestException("poolTotal must be non-negative");
        }
    }

    /** Package-private — pinned by the unit test. */
    static void validateCycleTransition(CompCycleStatus from, CompCycleStatus to) {
        if (from == CompCycleStatus.CLOSED || from == CompCycleStatus.CANCELLED) {
            throw new BadRequestException(
                    "Cycle in " + from + " state cannot transition further");
        }
        if (from == CompCycleStatus.DRAFT
                && to != CompCycleStatus.OPEN && to != CompCycleStatus.CANCELLED) {
            throw new BadRequestException(
                    "DRAFT can only go to OPEN or CANCELLED (got " + to + ")");
        }
        if (from == CompCycleStatus.OPEN
                && to != CompCycleStatus.CLOSED && to != CompCycleStatus.CANCELLED) {
            throw new BadRequestException(
                    "OPEN can only go to CLOSED or CANCELLED (got " + to + ")");
        }
    }

    // ─── Team grid ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public TeamGrid teamGridFor(UUID cycleId, List<UUID> employeeIds) {
        CompCycle cycle = loadCycle(cycleId);
        Set<UUID> visible = accessScope.intersect(
                employeeIds == null ? null : new java.util.HashSet<>(employeeIds));
        if (visible == null) {
            // Unrestricted caller asked for everyone — fall back to the
            // explicit employee set the UI sent. If empty, return empty grid.
            visible = employeeIds == null ? new java.util.HashSet<>()
                    : new java.util.HashSet<>(employeeIds);
        }
        if (visible.isEmpty()) {
            return new TeamGrid(cycle.getId(), cycle.getName(), cycle.getStatus(),
                    cycle.getPoolTotal(), proposals.committedDelta(cycleId),
                    CompPlanningMath.remaining(cycle.getPoolTotal(),
                            proposals.committedDelta(cycleId)),
                    cycle.getCurrency(), List.of());
        }

        LocalDate today = LocalDate.now();
        Map<UUID, Employee> empById = new HashMap<>();
        for (Employee e : employees.findAllById(visible)) empById.put(e.getId(), e);

        List<TeamRow> rows = new ArrayList<>(visible.size());
        for (UUID empId : visible) {
            Employee e = empById.get(empId);
            if (e == null) continue;
            EmployeeCompensation comp;
            try { comp = compensations.getActiveOn(empId, today); }
            catch (Exception ex) { comp = null; }
            BigDecimal currentSalary = comp == null ? BigDecimal.ZERO : comp.getMonthlyBaseSalary();
            String currency = comp == null ? cycle.getCurrency() : comp.getCurrency();
            Optional<CompProposal> existing = proposals.findByCycleIdAndEmployeeId(cycleId, empId);
            ProposalResponse proposalResponse = existing.map(p ->
                    ProposalResponse.from(p,
                            e.getFirstName() + " " + e.getLastName(),
                            e.getEmployeeNo(),
                            CompPlanningMath.increasePercent(p.getCurrentSalary(), p.getProposedSalary())))
                    .orElse(null);
            rows.add(new TeamRow(
                    empId, e.getEmployeeNo(),
                    e.getFirstName() + " " + e.getLastName(),
                    e.getDepartmentName(),
                    currentSalary, currency, proposalResponse));
        }
        rows.sort(java.util.Comparator.comparing(TeamRow::employeeName,
                java.util.Comparator.nullsLast(String::compareToIgnoreCase)));

        BigDecimal committed = proposals.committedDelta(cycleId);
        return new TeamGrid(cycle.getId(), cycle.getName(), cycle.getStatus(),
                cycle.getPoolTotal(), committed,
                CompPlanningMath.remaining(cycle.getPoolTotal(), committed),
                cycle.getCurrency(), rows);
    }

    // ─── Proposals ──────────────────────────────────────────────────────

    @Transactional
    public ProposalResponse upsertProposal(ProposalRequest req) {
        CompCycle cycle = loadCycle(req.cycleId());
        if (cycle.getStatus() != CompCycleStatus.OPEN) {
            throw new BadRequestException("Cycle is not OPEN");
        }
        if (!employees.existsById(req.employeeId())) {
            throw new BadRequestException("Employee not found: " + req.employeeId());
        }
        if (req.proposedSalary() == null || req.proposedSalary().signum() < 0) {
            throw new BadRequestException("proposedSalary must be non-negative");
        }
        EmployeeCompensation comp;
        try { comp = compensations.getActiveOn(req.employeeId(), LocalDate.now()); }
        catch (Exception ex) { comp = null; }
        BigDecimal currentSalary = comp == null ? BigDecimal.ZERO : comp.getMonthlyBaseSalary();

        Optional<CompProposal> existing = proposals
                .findByCycleIdAndEmployeeId(req.cycleId(), req.employeeId());
        CompProposal p = existing.orElseGet(CompProposal::new);
        if (existing.isPresent()
                && (p.getStatus() == CompProposalStatus.APPROVED
                    || p.getStatus() == CompProposalStatus.REJECTED)) {
            throw new BadRequestException(
                    "Proposal already decided; can't edit a " + p.getStatus() + " row");
        }
        ProposalResponse before = existing.isPresent()
                ? ProposalResponse.from(p, null, null, null) : null;
        p.setCycleId(req.cycleId());
        p.setEmployeeId(req.employeeId());
        p.setCurrentSalary(currentSalary);
        p.setProposedSalary(req.proposedSalary());
        p.setCurrency(comp == null ? cycle.getCurrency() : comp.getCurrency());
        p.setRationale(req.rationale());
        p.setEffectiveOn(req.effectiveOn());
        if (existing.isEmpty()) {
            p.setStatus(CompProposalStatus.DRAFT);
            p.setProposedBy(currentRequest.username());
            p.setProposedAt(OffsetDateTime.now());
        }
        CompProposal saved = proposals.save(p);
        Employee emp = employees.findById(req.employeeId()).orElse(null);
        ProposalResponse response = decorateProposal(saved, emp);
        audit.record(MODULE, PROPOSAL_ENTITY, saved.getId().toString(),
                existing.isPresent() ? "UPDATE" : "CREATE", before, response);
        return response;
    }

    @Transactional
    public ProposalResponse submitProposal(UUID id) {
        CompProposal p = loadProposal(id);
        if (p.getStatus() != CompProposalStatus.DRAFT) {
            throw new BadRequestException(
                    "Can only submit a DRAFT proposal (currently " + p.getStatus() + ")");
        }
        ProposalResponse before = decorateProposal(p, null);
        p.setStatus(CompProposalStatus.SUBMITTED);
        CompProposal saved = proposals.save(p);
        Employee emp = employees.findById(saved.getEmployeeId()).orElse(null);
        ProposalResponse response = decorateProposal(saved, emp);
        audit.record(MODULE, PROPOSAL_ENTITY, id.toString(),
                "SUBMIT", before, response);
        return response;
    }

    @Transactional
    public ProposalResponse decideProposal(UUID id, DecisionRequest req) {
        if (req.decision() != CompProposalStatus.APPROVED
                && req.decision() != CompProposalStatus.REJECTED) {
            throw new BadRequestException(
                    "Decision must be APPROVED or REJECTED (got " + req.decision() + ")");
        }
        CompProposal p = loadProposal(id);
        if (p.getStatus() != CompProposalStatus.SUBMITTED) {
            throw new BadRequestException(
                    "Can only decide a SUBMITTED proposal (currently " + p.getStatus() + ")");
        }
        ProposalResponse before = decorateProposal(p, null);
        p.setStatus(req.decision());
        p.setDecidedBy(currentRequest.username());
        p.setDecidedAt(OffsetDateTime.now());
        p.setDecisionNote(req.note());
        if (req.effectiveOn() != null) p.setEffectiveOn(req.effectiveOn());

        if (req.decision() == CompProposalStatus.APPROVED) {
            // Write through to EmployeeCompensation so payroll picks it up.
            LocalDate effective = p.getEffectiveOn() == null
                    ? LocalDate.now() : p.getEffectiveOn();
            EmployeeCompensation newComp = new EmployeeCompensation();
            newComp.setEmployeeId(p.getEmployeeId());
            newComp.setMonthlyBaseSalary(p.getProposedSalary());
            newComp.setCurrency(p.getCurrency());
            newComp.setEffectiveFrom(effective);
            newComp.setReason("Approved via comp cycle " + p.getCycleId());
            compensations.upsert(newComp);
        }

        CompProposal saved = proposals.save(p);
        Employee emp = employees.findById(saved.getEmployeeId()).orElse(null);
        ProposalResponse response = decorateProposal(saved, emp);
        audit.record(MODULE, PROPOSAL_ENTITY, id.toString(),
                req.decision().name(), before, response);
        return response;
    }

    @Transactional(readOnly = true)
    public List<ProposalResponse> proposalsForCycle(UUID cycleId) {
        return proposals.findByCycleIdOrderByProposedAtDesc(cycleId).stream()
                .map(p -> decorateProposal(p,
                        employees.findById(p.getEmployeeId()).orElse(null)))
                .toList();
    }

    private CompProposal loadProposal(UUID id) {
        return proposals.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Comp proposal not found: " + id));
    }

    private ProposalResponse decorateProposal(CompProposal p, Employee emp) {
        Double pct = CompPlanningMath.increasePercent(p.getCurrentSalary(), p.getProposedSalary());
        String name = emp == null ? null : (emp.getFirstName() + " " + emp.getLastName());
        String empNo = emp == null ? null : emp.getEmployeeNo();
        return ProposalResponse.from(p, name, empNo, pct);
    }
}
