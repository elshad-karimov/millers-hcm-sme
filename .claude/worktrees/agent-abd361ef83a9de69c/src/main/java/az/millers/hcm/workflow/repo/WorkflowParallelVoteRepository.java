package az.millers.hcm.workflow.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.workflow.domain.WorkflowParallelVote;

public interface WorkflowParallelVoteRepository extends JpaRepository<WorkflowParallelVote, UUID> {

    List<WorkflowParallelVote> findByInstanceIdAndStepOrder(UUID instanceId, int stepOrder);

    Optional<WorkflowParallelVote> findByInstanceIdAndStepId(UUID instanceId, UUID stepId);
}
