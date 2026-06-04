package az.millers.hcm.lifecycle.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.lifecycle.domain.ChecklistTemplateTask;

public interface ChecklistTemplateTaskRepository
        extends JpaRepository<ChecklistTemplateTask, UUID> {

    List<ChecklistTemplateTask> findByTemplateIdOrderByStepOrderAsc(UUID templateId);

    void deleteByTemplateId(UUID templateId);
}
