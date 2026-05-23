package az.millers.hcm.performance.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.performance.domain.Goal;

public interface GoalRepository extends JpaRepository<Goal, UUID> {

    @Query(value = "SELECT nextval('performance.goal_no_seq')", nativeQuery = true)
    long nextNoSequence();

    List<Goal> findByCycleIdAndEmployeeIdOrderByCreatedAt(UUID cycleId, UUID employeeId);

    List<Goal> findByCycleIdOrderByEmployeeIdAscCreatedAtAsc(UUID cycleId);

    List<Goal> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);
}
