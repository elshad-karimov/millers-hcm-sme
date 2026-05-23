package az.millers.hcm.workflow.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.workflow.domain.WorkflowAction;

public interface WorkflowActionRepository extends JpaRepository<WorkflowAction, UUID> {

    List<WorkflowAction> findByInstanceIdOrderByCreatedAtAsc(UUID instanceId);
}
