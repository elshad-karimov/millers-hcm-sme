package az.millers.hcm.performance.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.performance.domain.Goal;

public interface GoalRepository extends JpaRepository<Goal, UUID> {

    @Query(value = "SELECT config.next_tenant_seq('performance.goal_no_seq')", nativeQuery = true)
    long nextNoSequence();

    List<Goal> findByCycleIdAndEmployeeIdOrderByCreatedAt(UUID cycleId, UUID employeeId);

    List<Goal> findByCycleIdOrderByEmployeeIdAscCreatedAtAsc(UUID cycleId);

    List<Goal> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);

    /** M392 — the goal plan covered by one GOAL_APPROVAL workflow instance. */
    List<Goal> findByWorkflowInstanceId(UUID workflowInstanceId);

    /**
     * Returns all open DEVELOPMENT goals linked to the given course + employee.
     * Used by {@link az.millers.hcm.performance.service.GoalAutoRatingService}
     * to auto-rate on course completion (M49).
     */
    @org.springframework.data.jpa.repository.Query("""
            select g from Goal g
            where g.sourceCourseId = :courseId
              and g.employeeId     = :employeeId
              and g.category       = az.millers.hcm.performance.domain.GoalCategory.DEVELOPMENT
              and g.status not in (
                  az.millers.hcm.performance.domain.GoalStatus.ACHIEVED,
                  az.millers.hcm.performance.domain.GoalStatus.MISSED,
                  az.millers.hcm.performance.domain.GoalStatus.CANCELLED
              )
            """)
    List<Goal> findOpenDevelopmentGoalsForCourse(
            @org.springframework.data.repository.query.Param("courseId")   UUID courseId,
            @org.springframework.data.repository.query.Param("employeeId") UUID employeeId);
}
