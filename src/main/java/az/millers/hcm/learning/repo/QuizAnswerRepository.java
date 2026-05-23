package az.millers.hcm.learning.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.learning.domain.QuizAnswer;

public interface QuizAnswerRepository extends JpaRepository<QuizAnswer, UUID> {

    List<QuizAnswer> findByAttemptIdOrderById(UUID attemptId);
}
