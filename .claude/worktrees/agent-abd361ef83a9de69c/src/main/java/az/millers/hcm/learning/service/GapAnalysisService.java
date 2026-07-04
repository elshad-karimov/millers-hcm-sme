package az.millers.hcm.learning.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.learning.api.dto.GapItem;
import az.millers.hcm.learning.api.dto.GapItem.CourseRecommendation;
import az.millers.hcm.learning.api.dto.PositionRequirementRequest;
import az.millers.hcm.learning.api.dto.PositionRequirementResponse;
import az.millers.hcm.learning.domain.Course;
import az.millers.hcm.learning.domain.CourseCompetency;
import az.millers.hcm.learning.domain.CourseStatus;
import az.millers.hcm.learning.domain.EmployeeCompetency;
import az.millers.hcm.learning.domain.PositionCompetencyRequirement;
import az.millers.hcm.learning.repo.CompetencyRepository;
import az.millers.hcm.learning.repo.CourseCompetencyRepository;
import az.millers.hcm.learning.repo.CourseRepository;
import az.millers.hcm.learning.repo.EmployeeCompetencyRepository;
import az.millers.hcm.learning.repo.PositionCompetencyRequirementRepository;

/**
 * M60 (PRD Could-Have §7.3): Competency gap analysis with training recommendations.
 *
 * <p>Manages the {@code position_competency_requirement} catalogue and exposes a
 * per-employee gap report that compares current competency levels against what
 * the employee's assigned position demands.  Each gap row is enriched with a list
 * of published courses that award the relevant competency.
 */
@Service
public class GapAnalysisService {

    private final PositionCompetencyRequirementRepository requirements;
    private final EmployeeCompetencyRepository employeeCompetencies;
    private final CourseCompetencyRepository courseCompetencies;
    private final CourseRepository courses;
    private final CompetencyRepository competencies;
    private final EmployeeRepository employees;

    public GapAnalysisService(PositionCompetencyRequirementRepository requirements,
                               EmployeeCompetencyRepository employeeCompetencies,
                               CourseCompetencyRepository courseCompetencies,
                               CourseRepository courses,
                               CompetencyRepository competencies,
                               EmployeeRepository employees) {
        this.requirements = requirements;
        this.employeeCompetencies = employeeCompetencies;
        this.courseCompetencies = courseCompetencies;
        this.courses = courses;
        this.competencies = competencies;
        this.employees = employees;
    }

    // ── Position requirements CRUD ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PositionRequirementResponse> requirementsForPosition(UUID positionId) {
        return requirements.findByPositionIdOrderByCompetencyNameAsc(positionId)
                .stream()
                .map(PositionRequirementResponse::from)
                .toList();
    }

    @Transactional
    public PositionRequirementResponse addOrUpdateRequirement(UUID positionId,
                                                               PositionRequirementRequest req) {
        var competency = competencies.findById(req.competencyId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Competency not found: " + req.competencyId()));

        // Upsert — overwrite if already exists
        var pcr = requirements.findByPositionIdAndCompetencyId(positionId, req.competencyId())
                .orElseGet(PositionCompetencyRequirement::new);

        pcr.setPositionId(positionId);
        pcr.setCompetency(competency);
        pcr.setRequiredProficiency(req.requiredProficiency());
        pcr.setNotes(req.notes());

        return PositionRequirementResponse.from(requirements.save(pcr));
    }

    @Transactional
    public void removeRequirement(UUID positionId, UUID competencyId) {
        requirements.deleteByPositionIdAndCompetencyId(positionId, competencyId);
    }

    // ── Gap analysis ───────────────────────────────────────────────────────────

    /**
     * Runs a gap analysis for {@code employeeId}.
     *
     * <ol>
     *   <li>Resolves the employee's current position.</li>
     *   <li>Loads requirements for that position.</li>
     *   <li>Loads the employee's best (max) proficiency per competency.</li>
     *   <li>Computes the gap and recommends published courses for each competency
     *       where the employee falls short.</li>
     * </ol>
     *
     * @return all requirement rows, sorted so gaps come first (largest gap first),
     *         then met/exceeded requirements.
     * @throws BadRequestException if the employee has no position assigned.
     */
    @Transactional(readOnly = true)
    public List<GapItem> gapAnalysis(UUID employeeId) {
        var employee = employees.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));

        UUID positionId = employee.getPositionId();
        if (positionId == null) {
            throw new BadRequestException(
                    "Employee has no position assigned — cannot run gap analysis.");
        }

        List<PositionCompetencyRequirement> reqs =
                requirements.findByPositionIdOrderByCompetencyNameAsc(positionId);

        if (reqs.isEmpty()) {
            return List.of(); // no requirements defined for this position
        }

        // Build best-proficiency map: competencyId → max level across all employee_competency rows
        // EmployeeCompetency stores a plain UUID (not a @ManyToOne) so we call getCompetencyId().
        Map<UUID, Integer> bestLevel = employeeCompetencies
                .findByEmployeeIdOrderByAwardedAtDesc(employeeId)
                .stream()
                .collect(Collectors.toMap(
                        EmployeeCompetency::getCompetencyId,
                        EmployeeCompetency::getProficiency,
                        Math::max));

        // Build course-lookup map for all competencies in the requirements list
        // competencyId → list of published courses that award this competency
        Map<UUID, List<CourseRecommendation>> courseMap = buildCourseMap(
                reqs.stream().map(r -> r.getCompetency().getId()).toList());

        List<GapItem> result = new ArrayList<>();
        for (var req : reqs) {
            UUID cId = req.getCompetency().getId();
            Integer current = bestLevel.get(cId);
            int currentLevel = current != null ? current : 0;
            int gap = req.getRequiredProficiency() - currentLevel;

            List<CourseRecommendation> recs = gap > 0
                    ? courseMap.getOrDefault(cId, List.of())
                    : List.of();

            result.add(new GapItem(
                    cId,
                    req.getCompetency().getCode(),
                    req.getCompetency().getName(),
                    req.getCompetency().getCategory().name(),
                    req.getRequiredProficiency(),
                    current,           // null if never assessed
                    gap,
                    recs
            ));
        }

        // Sort: largest gap first, then alphabetically by name
        result.sort((a, b) -> {
            int cmp = Integer.compare(b.gap(), a.gap());
            if (cmp != 0) return cmp;
            return a.competencyName().compareTo(b.competencyName());
        });

        return result;
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    /**
     * For each competency ID, collect PUBLISHED courses that award it, sorted by
     * awarded level descending (highest proficiency first — most valuable courses
     * appear at the top of the recommendations list).
     */
    private Map<UUID, List<CourseRecommendation>> buildCourseMap(List<UUID> competencyIds) {
        // Load all published courses once (small catalogue in practice)
        Map<UUID, Course> publishedById = courses
                .findByStatusOrderByTitleAsc(CourseStatus.PUBLISHED)
                .stream()
                .collect(Collectors.toMap(Course::getId, c -> c));

        return competencyIds.stream().collect(Collectors.toMap(
                cId -> cId,
                cId -> courseCompetencies.findByCompetencyId(cId)
                        .stream()
                        .filter(cc -> publishedById.containsKey(cc.getCourseId()))
                        .sorted((a, b) -> Integer.compare(b.getAwardedLevel(), a.getAwardedLevel()))
                        .map(cc -> {
                            Course c = publishedById.get(cc.getCourseId());
                            return new CourseRecommendation(
                                    c.getId(), c.getCode(), c.getTitle(), cc.getAwardedLevel());
                        })
                        .toList()
        ));
    }
}
