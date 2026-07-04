package az.millers.hcm.recruitment.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.recruitment.domain.ScreeningQuestion;

public interface ScreeningQuestionRepository extends JpaRepository<ScreeningQuestion, UUID> {

    List<ScreeningQuestion> findByPostingIdOrderByOrdinalAscCreatedAtAsc(UUID postingId);

    List<ScreeningQuestion> findByPostingIdAndActiveTrueOrderByOrdinalAscCreatedAtAsc(UUID postingId);
}
