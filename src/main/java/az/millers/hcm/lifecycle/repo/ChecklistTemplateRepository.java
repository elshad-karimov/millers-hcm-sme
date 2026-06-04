package az.millers.hcm.lifecycle.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.lifecycle.domain.ChecklistFlowType;
import az.millers.hcm.lifecycle.domain.ChecklistTemplate;

public interface ChecklistTemplateRepository extends JpaRepository<ChecklistTemplate, UUID> {

    List<ChecklistTemplate> findByFlowTypeAndActiveTrueOrderByName(ChecklistFlowType flow);

    Optional<ChecklistTemplate> findByCode(String code);
}
