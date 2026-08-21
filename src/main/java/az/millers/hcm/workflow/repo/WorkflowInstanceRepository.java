package az.millers.hcm.workflow.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** M330 — pending instances of the definitions that carry a named-approver step. */
    List<WorkflowInstance> findByStatusAndDefinitionIdInOrderByInitiatedAtDesc(
            WorkflowStatus status, java.util.Collection<UUID> definitionIds);

    /** M162 — instances explicitly delegated to a named user. */
    List<WorkflowInstance> findByStatusAndDelegatedToOrderByInitiatedAtDesc(
            WorkflowStatus status, String delegatedTo);

    /**
     * M172 — parallel gate inbox: PENDING instances where the user holds a
     * role that still appears in {@code current_step_roles} TEXT[].
     * Uses a native PostgreSQL {@code &&} array-overlap operator.
     */
    @Query(value = "SELECT wi.* FROM workflow.workflow_instance wi " +
                   "WHERE wi.status = 'PENDING' " +
                   "  AND wi.current_step_roles IS NOT NULL " +
                   "  AND wi.current_step_roles && CAST(:roles AS text[]) " +
                   "ORDER BY wi.initiated_at DESC",
           nativeQuery = true)
    List<WorkflowInstance> findPendingParallelForRoles(@Param("roles") String[] roles);
}
