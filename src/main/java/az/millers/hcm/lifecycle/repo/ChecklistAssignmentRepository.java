package az.millers.hcm.lifecycle.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.lifecycle.domain.ChecklistAssignment;
import az.millers.hcm.lifecycle.domain.ChecklistAssignmentStatus;
import az.millers.hcm.lifecycle.domain.ChecklistFlowType;

public interface ChecklistAssignmentRepository
        extends JpaRepository<ChecklistAssignment, UUID> {

    List<ChecklistAssignment> findByEmployeeIdOrderByStartedAtDesc(UUID employeeId);

    Optional<ChecklistAssignment> findByEmployeeIdAndFlowTypeAndStatus(
            UUID employeeId, ChecklistFlowType flowType, ChecklistAssignmentStatus status);

    List<ChecklistAssignment> findByStatusAndFlowTypeOrderByStartedAtDesc(
            ChecklistAssignmentStatus status, ChecklistFlowType flowType);
}
