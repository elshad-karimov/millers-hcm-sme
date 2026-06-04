package az.millers.hcm.engagement.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.engagement.domain.SurveyTemplate;

public interface SurveyTemplateRepository extends JpaRepository<SurveyTemplate, UUID> {

    boolean existsByCode(String code);

    List<SurveyTemplate> findAllByOrderByNameAsc();

    List<SurveyTemplate> findByActiveTrueOrderByNameAsc();
}
