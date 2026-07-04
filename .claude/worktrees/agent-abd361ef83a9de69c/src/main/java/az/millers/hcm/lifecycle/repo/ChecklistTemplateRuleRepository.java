package az.millers.hcm.lifecycle.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.lifecycle.domain.ChecklistTemplateRule;

public interface ChecklistTemplateRuleRepository extends JpaRepository<ChecklistTemplateRule, UUID> {

    List<ChecklistTemplateRule> findByTemplateId(UUID templateId);

    List<ChecklistTemplateRule> findByTemplateIdAndActiveTrue(UUID templateId);

    void deleteByTemplateId(UUID templateId);
}
