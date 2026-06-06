package az.millers.hcm.skills.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.learning.domain.Competency;
import az.millers.hcm.learning.domain.EmployeeCompetency;
import az.millers.hcm.learning.repo.CompetencyRepository;
import az.millers.hcm.learning.repo.EmployeeCompetencyRepository;
import az.millers.hcm.skills.api.SkillsDtos.EmployeeFitRow;
import az.millers.hcm.skills.api.SkillsDtos.EmployeeGapResponse;
import az.millers.hcm.skills.api.SkillsDtos.PositionFitResponse;
import az.millers.hcm.skills.api.SkillsDtos.SkillGapRow;
import az.millers.hcm.skills.domain.GapSeverity;
import az.millers.hcm.skills.service.SkillGapAnalyzer.EmployeeSkill;
import az.millers.hcm.skills.service.SkillGapAnalyzer.PositionRequirement;
import az.millers.hcm.skills.service.SkillGapAnalyzer.SkillGap;
import az.millers.hcm.staffing.domain.PositionRequiredCompetency;
import az.millers.hcm.staffing.repo.PositionRequiredCompetencyRepository;

/**
 * M127 — assembles the inputs and runs
 * {@link SkillGapAnalyzer} for the gap viewer + the
 * position-fit ranking.
 */
@Service
public class SkillGapService {

    /** Cap on how many candidates the fit endpoint scores in one call. */
    public static final int FIT_RANK_CAP = 500;

    private final EmployeeCompetencyRepository empCompRepo;
    private final PositionRequiredCompetencyRepository requirementsRepo;
    private final CompetencyRepository competencies;
    private final EmployeeRepository employees;

    public SkillGapService(EmployeeCompetencyRepository empCompRepo,
                           PositionRequiredCompetencyRepository requirementsRepo,
                           CompetencyRepository competencies,
                           EmployeeRepository employees) {
        this.empCompRepo = empCompRepo;
        this.requirementsRepo = requirementsRepo;
        this.competencies = competencies;
        this.employees = employees;
    }

    @Transactional(readOnly = true)
    public EmployeeGapResponse gapForEmployee(UUID employeeId, UUID positionId) {
        if (!employees.existsById(employeeId)) {
            throw new ResourceNotFoundException("Employee not found: " + employeeId);
        }
        List<PositionRequiredCompetency> reqs = requirementsRepo
                .findByPositionIdOrderByMandatoryDescRequiredLevelDesc(positionId);
        if (reqs.isEmpty()) {
            return new EmployeeGapResponse(employeeId, positionId,
                    100, 0, 0, 0, 0, 0, List.of());
        }
        List<EmployeeCompetency> empComps = empCompRepo
                .findByEmployeeIdOrderByAwardedAtDesc(employeeId);
        List<EmployeeSkill> empSkills = mapEmpSkills(empComps);
        List<PositionRequirement> requirements = mapRequirements(reqs);
        LocalDate today = LocalDate.now();
        List<SkillGap> gaps = SkillGapAnalyzer.analyze(empSkills, requirements, today);
        int score = SkillGapAnalyzer.fitScore(empSkills, requirements, today);

        Map<UUID, Competency> compsById = loadCompetencies(reqs);
        int blockers = 0, majors = 0, minors = 0, met = 0;
        List<SkillGapRow> rows = new ArrayList<>(gaps.size());
        for (SkillGap g : gaps) {
            Competency c = compsById.get(g.competencyId());
            rows.add(new SkillGapRow(
                    g.competencyId(),
                    c == null ? null : c.getCode(),
                    c == null ? null : c.getName(),
                    g.requiredLevel(), g.currentLevel(), g.mandatory(), g.severity()));
            switch (g.severity()) {
                case BLOCKER -> blockers++;
                case MAJOR -> majors++;
                case MINOR -> minors++;
                case NONE -> met++;
            }
        }
        return new EmployeeGapResponse(
                employeeId, positionId, score, reqs.size(),
                blockers, majors, minors, met, rows);
    }

    /**
     * Rank all active employees by fit-score against the position.
     * Capped at {@link #FIT_RANK_CAP} — past that, paginated UX is the
     * Phase 2 fix.
     */
    @Transactional(readOnly = true)
    public PositionFitResponse rankCandidatesForPosition(UUID positionId) {
        List<PositionRequiredCompetency> reqs = requirementsRepo
                .findByPositionIdOrderByMandatoryDescRequiredLevelDesc(positionId);
        if (reqs.isEmpty()) {
            return new PositionFitResponse(positionId, 0, List.of());
        }
        List<PositionRequirement> requirements = mapRequirements(reqs);
        LocalDate today = LocalDate.now();
        // Pull every active employee. For organisations beyond 500 active
        // employees this needs the Phase 2 paged variant.
        List<Employee> active = employees.findAll().stream()
                .filter(e -> e.getEmploymentStatus() != null
                        && "ACTIVE".equalsIgnoreCase(e.getEmploymentStatus().name()))
                .toList();
        int total = active.size();
        if (active.size() > FIT_RANK_CAP) active = active.subList(0, FIT_RANK_CAP);

        List<EmployeeFitRow> rows = new ArrayList<>(active.size());
        for (Employee e : active) {
            List<EmployeeCompetency> empComps = empCompRepo
                    .findByEmployeeIdOrderByAwardedAtDesc(e.getId());
            List<EmployeeSkill> skills = mapEmpSkills(empComps);
            int score = SkillGapAnalyzer.fitScore(skills, requirements, today);
            int blockers = 0, majors = 0;
            for (SkillGap g : SkillGapAnalyzer.analyze(skills, requirements, today)) {
                if (g.severity() == GapSeverity.BLOCKER) blockers++;
                if (g.severity() == GapSeverity.MAJOR) majors++;
            }
            rows.add(new EmployeeFitRow(
                    e.getId(), e.getEmployeeNo(),
                    e.getFirstName() + " " + e.getLastName(),
                    score, blockers, majors));
        }
        rows.sort((a, b) -> Integer.compare(b.fitScore(), a.fitScore()));
        return new PositionFitResponse(positionId, total, rows);
    }

    // ── Mapping ─────────────────────────────────────────────────────────────

    private static List<EmployeeSkill> mapEmpSkills(List<EmployeeCompetency> rows) {
        List<EmployeeSkill> out = new ArrayList<>(rows.size());
        for (EmployeeCompetency r : rows) {
            out.add(new EmployeeSkill(
                    r.getCompetencyId(),
                    r.getProficiency(),
                    r.getValidUntil()));
        }
        return out;
    }

    private static List<PositionRequirement> mapRequirements(List<PositionRequiredCompetency> rows) {
        List<PositionRequirement> out = new ArrayList<>(rows.size());
        for (PositionRequiredCompetency r : rows) {
            out.add(new PositionRequirement(
                    r.getCompetencyId(),
                    r.getRequiredLevel(),
                    r.isMandatory()));
        }
        return out;
    }

    private Map<UUID, Competency> loadCompetencies(List<PositionRequiredCompetency> rows) {
        List<UUID> ids = rows.stream().map(PositionRequiredCompetency::getCompetencyId).distinct().toList();
        if (ids.isEmpty()) return Collections.emptyMap();
        Map<UUID, Competency> out = new HashMap<>();
        for (Competency c : competencies.findAllById(ids)) out.put(c.getId(), c);
        return out;
    }
}
