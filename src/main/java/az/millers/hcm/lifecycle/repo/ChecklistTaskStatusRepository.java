package az.millers.hcm.lifecycle.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.lifecycle.domain.ChecklistTaskStatus;

public interface ChecklistTaskStatusRepository
        extends JpaRepository<ChecklistTaskStatus, UUID> {

    List<ChecklistTaskStatus> findByAssignmentIdOrderByStepOrderAsc(UUID assignmentId);
}
