package az.millers.hcm.learning.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.domain.EmploymentStatus;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.learning.api.dto.PositionFitResponse;
import az.millers.hcm.learning.api.dto.PositionFitResponse.EmployeeFitRow;
import az.millers.hcm.learning.domain.EmployeeCompetency;
import az.millers.hcm.learning.domain.PositionCompetencyRequirement;
import az.millers.hcm.learning.repo.EmployeeCompetencyRepository;
import az.millers.hcm.learning.repo.PositionCompetencyRequirementRepository;
import az.millers.hcm.learning.service.PositionFitAnalyzer.EmployeeSkill;
import az.millers.hcm.learning.service.PositionFitAnalyzer.Gap;
import az.millers.hcm.learning.service.PositionFitAnalyzer.Requirement;
import az.millers.hcm.learning.service.PositionFitAnalyzer.Severity;

/**
 * Candidate-fit ranking — rebuilt on the canonical learning tables after the
 * retired {@code skills.SkillGapService} was removed in the dedup ({@code 0d9725b}).
 *
 * <p>Requirements come from {@code learning.position_competency_requirement}
 * (via {@link PositionCompetencyRequirementRepository}); employee proficiencies
 * from {@code learning.employee_competency} (via
 * {@link EmployeeCompetencyRepository#findByEmployeeIdOrderByAwardedAtDesc} — the
 * same source {@link GapAnalysisService} reads). The scoring math lives in the
 * pure {@link PositionFitAnalyzer}.
 */
@Service
public class PositionFitService {

    /** Cap on how many active employees the fit endpoint scores in one call. */
    public static final int FIT_RANK_CAP = 500;

    private final PositionCompetencyRequirementRepository requirements;
    private final EmployeeCompetencyRepository employeeCompetencies;
    private final EmployeeRepository employees;

    public PositionFitService(PositionCompetencyRequirementRepository requirements,
                              EmployeeCompetencyRepository employeeCompetencies,
                              EmployeeRepository employees) {
        this.requirements = requirements;
        this.employeeCompetencies = employeeCompetencies;
        this.employees = employees;
    }

    /**
     * Rank active employees by fit against a position's competency requirements,
     * best-first. Capped at {@link #FIT_RANK_CAP}; {@code totalCandidates}
     * reports the full active headcount considered before the cap.
     */
    @Transactional(readOnly = true)
    public PositionFitResponse rankCandidatesForPosition(UUID positionId) {
        List<PositionCompetencyRequirement> reqs =
                requirements.findByPositionIdOrderByCompetencyNameAsc(positionId);
        if (reqs.isEmpty()) {
            return new PositionFitResponse(positionId, 0, List.of());
        }
        List<Requirement> requirementModel = mapRequirements(reqs);
        LocalDate today = LocalDate.now();

        List<Employee> active = employees.findAllByEmploymentStatus(EmploymentStatus.ACTIVE);
        int total = active.size();
        if (active.size() > FIT_RANK_CAP) active = active.subList(0, FIT_RANK_CAP);

        List<EmployeeFitRow> rows = new ArrayList<>(active.size());
        for (Employee e : active) {
            List<EmployeeSkill> skills = mapEmpSkills(
                    employeeCompetencies.findByEmployeeIdOrderByAwardedAtDesc(e.getId()));
            int score = PositionFitAnalyzer.fitScore(skills, requirementModel, today);
            int blockers = 0, majors = 0;
            for (Gap g : PositionFitAnalyzer.analyze(skills, requirementModel, today)) {
                if (g.severity() == Severity.BLOCKER) blockers++;
                else if (g.severity() == Severity.MAJOR) majors++;
            }
            rows.add(new EmployeeFitRow(
                    e.getId(), e.getEmployeeNo(),
                    fullName(e), score, blockers, majors));
        }
        // Best fit first; deterministic tie-break on name then employee id so the
        // ranking is stable across runs when scores collide.
        rows.sort(Comparator.comparingInt(EmployeeFitRow::fitScore).reversed()
                .thenComparing(r -> r.employeeName() == null ? "" : r.employeeName())
                .thenComparing(r -> r.employeeId().toString()));
        return new PositionFitResponse(positionId, total, rows);
    }

    // ── mapping ──────────────────────────────────────────────────────────────

    private static List<EmployeeSkill> mapEmpSkills(List<EmployeeCompetency> rows) {
        List<EmployeeSkill> out = new ArrayList<>(rows.size());
        for (EmployeeCompetency r : rows) {
            out.add(new EmployeeSkill(r.getCompetencyId(), r.getProficiency(), r.getValidUntil()));
        }
        return out;
    }

    private static List<Requirement> mapRequirements(List<PositionCompetencyRequirement> rows) {
        List<Requirement> out = new ArrayList<>(rows.size());
        for (PositionCompetencyRequirement r : rows) {
            out.add(new Requirement(
                    r.getCompetency().getId(),
                    r.getRequiredProficiency(),
                    r.isMandatory()));
        }
        return out;
    }

    private static String fullName(Employee e) {
        String first = e.getFirstName() == null ? "" : e.getFirstName();
        String last = e.getLastName() == null ? "" : e.getLastName();
        return (first + " " + last).trim();
    }
}
