package az.millers.hcm.learning.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.learning.domain.QuizQuestion;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, UUID> {

    List<QuizQuestion> findByCourseIdOrderByQuestionNoAsc(UUID courseId);

    long countByCourseId(UUID courseId);
}
