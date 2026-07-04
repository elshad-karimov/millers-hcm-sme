package az.millers.hcm.performance.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.performance.domain.CareerInterest;
import az.millers.hcm.performance.domain.DevelopmentPlan;
import az.millers.hcm.performance.domain.MentoringRelationship;
import az.millers.hcm.performance.repo.CareerInterestRepository;
import az.millers.hcm.performance.repo.DevelopmentPlanRepository;
import az.millers.hcm.performance.repo.MentoringRelationshipRepository;
import az.millers.hcm.security.scope.AccessScopeService;
import az.millers.hcm.staffing.domain.CareerPathStep;
import az.millers.hcm.staffing.repo.CareerPathStepRepository;

/**
 * HCM_16 M418 — career dashboard (PRD §16.8).
 * Employee self-service: interests + dev plans + mentoring + matching career paths
 * + recommended internal jobs (scored). AccessScope-guarded: employees see own.
 */
@Service
public class CareerDashboardService {

    private final CareerInterestRepository interests;
    private final DevelopmentPlanRepository devPlans;
    private final MentoringRelationshipRepository mentoring;
    private final CareerPathStepRepository careerPathSteps;
    private final AccessScopeService accessScope;
    private final NamedParameterJdbcTemplate jdbc;

    public CareerDashboardService(CareerInterestRepository interests,
                                   DevelopmentPlanRepository devPlans,
                                   MentoringRelationshipRepository mentoring,
                                   CareerPathStepRepository careerPathSteps,
                                   AccessScopeService accessScope,
                                   NamedParameterJdbcTemplate jdbc) {
        this.interests = interests;
        this.devPlans = devPlans;
        this.mentoring = mentoring;
        this.careerPathSteps = careerPathSteps;
        this.accessScope = accessScope;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(UUID employeeId) {
        // Access guard
        if (!accessScope.isAccessible(employeeId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Cannot view career dashboard for another employee");
        }

        // Career interests
        List<CareerInterest> interestsList = interests.findByTenantIdAndEmployeeIdOrderByCreatedAtDesc("default", employeeId);
        List<InterestInfo> interestInfos = interestsList.stream()
                .map(i -> new InterestInfo(i.getId(), i.getTargetRole(), i.getTargetDepartment(), i.getTargetLocation()))
                .toList();

        // Development plans
        List<DevelopmentPlan> plansList = devPlans.findByTenantIdAndEmployeeIdOrderByCreatedAtDesc("default", employeeId);
        List<DevPlanInfo> planInfos = plansList.stream()
                .map(p -> new DevPlanInfo(p.getId(), p.getTitle(), p.getStatus()))
                .toList();

        // Mentoring relationships
        List<MentoringRelationship> mentorList = mentoring.findByMentorEmployeeIdOrMenteeEmployeeId(employeeId, employeeId);
        List<MentoringInfo> mentoringInfos = mentorList.stream()
                .map(m -> new MentoringInfo(
                        m.getId(),
                        m.getMentorEmployeeId(),
                        m.getMenteeEmployeeId(),
                        m.getStatus()
                ))
                .toList();

        // Matching career paths (paths having a step with from_position_id = employee's position)
        UUID empPosition = loadEmployeePosition(employeeId);
        List<CareerPathMatch> pathMatches = new ArrayList<>();
        if (empPosition != null) {
            List<CareerPathStep> matchingSteps = careerPathSteps.findAll().stream()
                    .filter(s -> empPosition.equals(s.getFromPositionId()))
                    .toList();
            for (CareerPathStep step : matchingSteps) {
                var path = loadCareerPath(step.getPathId());
                if (path != null) {
                    pathMatches.add(new CareerPathMatch(path.id, path.code, path.name, step.getToPositionId()));
                }
            }
        }

        // Internal job recommendations (score: 2 if interest matches posting title/dept, 1 if dept matches)
        List<JobRecommendation> jobRecs = recommendJobs(employeeId, interestsList);

        return new DashboardResponse(interestInfos, planInfos, mentoringInfos, pathMatches, jobRecs);
    }

    private List<JobRecommendation> recommendJobs(UUID employeeId, List<CareerInterest> interestsList) {
        // Load open internal job postings
        List<JobPosting> openJobs = jdbc.query(
                """
                SELECT id::text, job_title, department, status
                FROM recruitment.job_posting
                WHERE status IN ('PUBLISHED', 'OPEN') AND is_internal = true
                """,
                (rs, i) -> new JobPosting(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("job_title"),
                        rs.getString("department"),
                        rs.getString("status")
                )
        );

        List<JobRecommendation> recs = new ArrayList<>();
        for (JobPosting job : openJobs) {
            int score = 0;
            for (CareerInterest interest : interestsList) {
                // Score 2: target_role matches posting title (either direction, case-insensitive)
                if (interest.getTargetRole() != null && job.title != null) {
                    if (interest.getTargetRole().toLowerCase().contains(job.title.toLowerCase())
                        || job.title.toLowerCase().contains(interest.getTargetRole().toLowerCase())) {
                        score += 2;
                    }
                }
                // Score 1: target_department matches posting department
                if (interest.getTargetDepartment() != null && job.department != null
                    && interest.getTargetDepartment().equalsIgnoreCase(job.department)) {
                    score += 1;
                }
            }
            if (score > 0) {
                recs.add(new JobRecommendation(job.id, job.title, job.department, score));
            }
        }
        recs.sort((a, b) -> Integer.compare(b.score, a.score));
        return recs;
    }

    private UUID loadEmployeePosition(UUID empId) {
        var params = new MapSqlParameterSource("id", empId);
        List<String> positions = jdbc.query(
                "SELECT position_id::text FROM core_hr.employee WHERE id = :id",
                params,
                (rs, i) -> rs.getString("position_id")
        );
        return positions.isEmpty() || positions.get(0) == null ? null : UUID.fromString(positions.get(0));
    }

    private CareerPathInfo loadCareerPath(UUID pathId) {
        var params = new MapSqlParameterSource("id", pathId);
        List<CareerPathInfo> paths = jdbc.query(
                "SELECT id::text, code, name FROM staffing.career_path WHERE id = :id",
                params,
                (rs, i) -> new CareerPathInfo(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("code"),
                        rs.getString("name")
                )
        );
        return paths.isEmpty() ? null : paths.get(0);
    }

    record JobPosting(UUID id, String title, String department, String status) {}
    record CareerPathInfo(UUID id, String code, String name) {}

    // ── DTOs ────────────────────────────────────────────────────────────────

    public record DashboardResponse(
            List<InterestInfo> interests,
            List<DevPlanInfo> devPlans,
            List<MentoringInfo> mentoring,
            List<CareerPathMatch> careerPaths,
            List<JobRecommendation> jobRecommendations) {}

    public record InterestInfo(UUID id, String targetRole, String targetDepartment, String targetLocation) {}
    public record DevPlanInfo(UUID id, String planName, String status) {}
    public record MentoringInfo(UUID id, UUID mentorId, UUID menteeId, String status) {}
    public record CareerPathMatch(UUID pathId, String pathCode, String pathName, UUID toPositionId) {}
    public record JobRecommendation(UUID jobId, String title, String department, int score) {}
}
