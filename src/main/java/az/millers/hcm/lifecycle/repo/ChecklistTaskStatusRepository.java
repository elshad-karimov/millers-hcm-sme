package az.millers.hcm.lifecycle.repo;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.lifecycle.domain.ChecklistAssignmentStatus;
import az.millers.hcm.lifecycle.domain.ChecklistFlowType;
import az.millers.hcm.lifecycle.domain.ChecklistTaskStatus;
import az.millers.hcm.lifecycle.domain.ChecklistTaskStatusValue;
import az.millers.hcm.lifecycle.domain.ChecklistTaskType;

public interface ChecklistTaskStatusRepository
        extends JpaRepository<ChecklistTaskStatus, UUID> {

    List<ChecklistTaskStatus> findByAssignmentIdOrderByStepOrderAsc(UUID assignmentId);

    /**
     * M303 — open onboarding training tasks for an employee that target a given
     * course/path, so a CoursePassedEvent can close the loop. Theta-joins to the
     * assignment (task status only carries assignmentId, not a mapped relation).
     */
    @Query("""
            select ts from ChecklistTaskStatus ts, ChecklistAssignment a
            where a.id = ts.assignmentId
              and a.employeeId = :employeeId
              and a.flowType = :flow
              and a.status = :assignmentStatus
              and ts.taskType = :taskType
              and ts.trainingTargetId = :targetId
              and ts.status in :openStatuses
            """)
    List<ChecklistTaskStatus> findOpenTrainingTasks(
            UUID employeeId,
            ChecklistFlowType flow,
            ChecklistAssignmentStatus assignmentStatus,
            ChecklistTaskType taskType,
            UUID targetId,
            Collection<ChecklistTaskStatusValue> openStatuses);
}
