package az.millers.hcm.workflow.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.workflow.domain.WorkflowStep;

public interface WorkflowStepRepository extends JpaRepository<WorkflowStep, UUID> {

    List<WorkflowStep> findByDefinitionIdOrderByStepOrderAsc(UUID definitionId);
}
