package az.millers.hcm.recruitment.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.recruitment.domain.InterviewQuestion;

public interface InterviewQuestionRepository extends JpaRepository<InterviewQuestion, UUID> {

    List<InterviewQuestion> findByKitIdOrderBySortOrderAscIdAsc(UUID kitId);

    List<InterviewQuestion> findByKitIdAndActiveTrueOrderBySortOrderAscIdAsc(UUID kitId);
}
