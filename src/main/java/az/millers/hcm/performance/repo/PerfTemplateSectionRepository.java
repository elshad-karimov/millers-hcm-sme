package az.millers.hcm.performance.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.performance.domain.PerfTemplateSection;

public interface PerfTemplateSectionRepository extends JpaRepository<PerfTemplateSection, UUID> {

    List<PerfTemplateSection> findByTemplateIdOrderBySectionOrderAsc(UUID templateId);

    void deleteByTemplateId(UUID templateId);
}
