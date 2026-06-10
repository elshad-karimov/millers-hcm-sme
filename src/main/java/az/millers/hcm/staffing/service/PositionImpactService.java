package az.millers.hcm.staffing.service;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.performance.repo.SuccessionNominationRepository;
import az.millers.hcm.recruitment.domain.Vacancy;
import az.millers.hcm.recruitment.domain.VacancyStatus;
import az.millers.hcm.recruitment.repo.VacancyRepository;
import az.millers.hcm.staffing.api.dto.PositionImpactReport;
import az.millers.hcm.staffing.api.dto.PositionImpactReport.AffectedEmployee;
import az.millers.hcm.staffing.api.dto.PositionImpactReport.AffectedVacancy;
import az.millers.hcm.staffing.domain.GrantStatus;
import az.millers.hcm.staffing.domain.Position;
import az.millers.hcm.staffing.domain.PositionBudget;
import az.millers.hcm.staffing.repo.PositionBudgetRepository;
import az.millers.hcm.staffing.repo.PositionProfileGrantRepository;
import az.millers.hcm.staffing.repo.PositionRepository;

/**
 * M272 — PRD §43 Impact Analysis.
 *
 * <p>Computes the blast radius of acting on a position. Used both by
 * the SPA confirm dialogs for CLOSE / FREEZE / SPLIT / MERGE and by
 * the dashboard tile that ranks positions by risk.
 */
@Service
public class PositionImpactService {

    private final PositionRepository positions;
    private final EmployeeRepository employees;
    private final VacancyRepository vacancies;
    private final PositionBudgetRepository budgets;
    private final PositionProfileGrantRepository grants;
    private final SuccessionNominationRepository nominations;

    public PositionImpactService(PositionRepository positions,
                                  EmployeeRepository employees,
                                  VacancyRepository vacancies,
                                  PositionBudgetRepository budgets,
                                  PositionProfileGrantRepository grants,
                                  SuccessionNominationRepository nominations) {
        this.positions = positions;
        this.employees = employees;
        this.vacancies = vacancies;
        this.budgets = budgets;
        this.grants = grants;
        this.nominations = nominations;
    }

    @Transactional(readOnly = true)
    public PositionImpactReport compute(UUID positionId) {
        Position p = positions.findById(positionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Position not found: " + positionId));

        // ── Occupants ─────────────────────────────────────────────
        List<Employee> occupantsRaw = employees.findActiveByPositionId(positionId);
        List<AffectedEmployee> occupants = occupantsRaw.stream()
                .map(e -> new AffectedEmployee(
                        e.getId(), e.getEmployeeNo(),
                        fullName(e)))
                .toList();

        // ── Direct reports of those occupants ─────────────────────
        // Closing the position effectively orphans these reports —
        // their manager_id will need to be reassigned.
        Set<UUID> seen = new HashSet<>();
        List<AffectedEmployee> directReports = occupantsRaw.stream()
                .flatMap(occ -> employees.findDirectReports(occ.getId()).stream())
                .filter(r -> seen.add(r.getId()))
                .map(r -> new AffectedEmployee(
                        r.getId(), r.getEmployeeNo(), fullName(r)))
                .toList();

        // ── Open vacancies ────────────────────────────────────────
        List<Vacancy> vacRaw = vacancies.findByPositionIdOrderByCreatedAtDesc(positionId);
        List<AffectedVacancy> openVacs = vacRaw.stream()
                .filter(v -> v.getStatus().isAccepting())
                .map(v -> new AffectedVacancy(
                        v.getId(), v.getVacancyNo(), v.getTitle(),
                        v.getStatus().name(), v.getOpenings()))
                .toList();

        // ── Active profile grants on occupants for this position ──
        // We count by looking at active grants on each occupant whose
        // position context matches. Operator sees "X grants will be
        // revoked if you close this seat."
        int activeGrants = (int) occupantsRaw.stream()
                .flatMap(e -> grants.findByEmployeeIdAndStatusOrderByCreatedAtDesc(
                        e.getId(), GrantStatus.ACTIVE).stream())
                .filter(g -> positionId.equals(g.getPositionId()))
                .count();

        // ── Budget ────────────────────────────────────────────────
        var budgetOpt = budgets.currentBudget(positionId, java.time.LocalDate.now());
        BigDecimal monthly = budgetOpt.map(this::sumBudgeted).orElse(null);
        String currency = budgetOpt.map(PositionBudget::getCurrency).orElse(p.getCurrency());

        // ── Successor coverage ───────────────────────────────────
        var noms = nominations.findByPositionIdAndCancelledAtIsNullOrderByCreatedAtDesc(positionId);
        int nominationCount = noms.size();
        boolean readyNow = noms.stream().anyMatch(n ->
                n.getReadinessTier() == az.millers.hcm.performance.api.dto.SuccessionGridDtos.Readiness.READY_NOW);
        boolean atRisk = (p.isCriticalFlag() && !readyNow)
                || (p.isSuccessorRequired() && nominationCount == 0);

        return new PositionImpactReport(
                p.getId(), p.getCode(), p.getTitle(),
                p.getApprovedHeadcount(), p.getOccupiedHeadcount(),
                Math.max(0, p.getApprovedHeadcount() - p.getOccupiedHeadcount()),
                p.getStatus().name(),
                p.isCriticalFlag(), p.isSuccessorRequired(),
                p.getBusinessImpactScore(),
                occupants, directReports, openVacs,
                activeGrants,
                monthly, currency,
                nominationCount, atRisk);
    }

    private BigDecimal sumBudgeted(PositionBudget b) {
        return nz(b.getBudgetedBasicSalary())
                .add(nz(b.getBudgetedAllowances()))
                .add(nz(b.getBudgetedEmployerTax()))
                .add(nz(b.getBudgetedBonus()))
                .add(nz(b.getBudgetedOvertime()))
                .add(nz(b.getBudgetedBenefits()));
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String fullName(Employee e) {
        return ((e.getFirstName() == null ? "" : e.getFirstName()) + " "
                + (e.getLastName() == null ? "" : e.getLastName())).trim();
    }
}
