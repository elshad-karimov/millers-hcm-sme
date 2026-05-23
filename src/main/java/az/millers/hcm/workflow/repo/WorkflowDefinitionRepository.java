package az.millers.hcm.workflow.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.workflow.domain.WorkflowDefinition;

public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinition, UUID> {

    Optional<WorkflowDefinition> findByCode(String code);

    List<WorkflowDefinition> findByActiveTrueOrderByCodeAsc();
}
