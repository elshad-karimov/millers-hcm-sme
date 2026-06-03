package az.millers.hcm.learning.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.learning.domain.LearningPathAssignment;
import az.millers.hcm.learning.domain.PathAssignmentStatus;

public interface LearningPathAssignmentRepository extends JpaRepository<LearningPathAssignment, UUID> {

    /** Active = non-terminal (ASSIGNED or IN_PROGRESS). Used by the partial unique check. */
    Optional<LearningPathAssignment> findFirstByPathIdAndEmployeeIdAndStatusIn(
            UUID pathId, UUID employeeId, List<PathAssignmentStatus> statuses);

    List<LearningPathAssignment> findByEmployeeIdOrderByAssignedAtDesc(UUID employeeId);

    List<LearningPathAssignment> findByPathIdOrderByAssignedAtDesc(UUID pathId);
}
