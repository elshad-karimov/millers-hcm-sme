package az.millers.hcm.recruitment.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.recruitment.domain.InterviewScore;

public interface InterviewScoreRepository extends JpaRepository<InterviewScore, UUID> {

    List<InterviewScore> findByInterviewId(UUID interviewId);

    Optional<InterviewScore> findByInterviewIdAndQuestionId(UUID interviewId, UUID questionId);

    void deleteByInterviewId(UUID interviewId);
}
