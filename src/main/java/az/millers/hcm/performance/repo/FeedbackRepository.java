package az.millers.hcm.performance.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.performance.domain.Feedback;

public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {

    List<Feedback> findByCycleIdAndSubjectEmployeeIdOrderByCreatedAtDesc(UUID cycleId, UUID subjectEmployeeId);

    List<Feedback> findByCycleIdOrderBySubjectEmployeeIdAscCreatedAtAsc(UUID cycleId);

    List<Feedback> findByAuthorEmployeeIdOrderByCreatedAtDesc(UUID authorEmployeeId);
}
