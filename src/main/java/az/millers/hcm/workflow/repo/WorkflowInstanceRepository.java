package az.millers.hcm.workflow.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.workflow.domain.WorkflowInstance;
import az.millers.hcm.workflow.domain.WorkflowStatus;

public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstance, UUID> {

    List<WorkflowInstance> findBySubjectModuleAndSubjectEntityAndSubjectIdOrderByInitiatedAtDesc(
            String subjectModule, String subjectEntity, String subjectId);

    List<WorkflowInstance> findByStatusAndCurrentStepRoleInOrderByInitiatedAtDesc(
            WorkflowStatus status, List<String> roles);

    List<WorkflowInstance> findByInitiatedByOrderByInitiatedAtDesc(String initiatedBy);

    /** M126 — feed for the SLA breach scheduler. */
    List<WorkflowInstance> findByStatusOrderByInitiatedAtAsc(WorkflowStatus status);

    /** M160 — Prometheus gauge: pending approval count. */
    long countByStatus(String status);

    /** M162 — instances explicitly delegated to a named user. */
    List<WorkflowInstance> findByStatusAndDelegatedToOrderByInitiatedAtDesc(
            WorkflowStatus status, String delegatedTo);
}
