package az.millers.hcm.recruitment.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.recruitment.domain.ApplicationAnswer;

public interface ApplicationAnswerRepository extends JpaRepository<ApplicationAnswer, UUID> {

    List<ApplicationAnswer> findByApplicationIdOrderByCreatedAtAsc(UUID applicationId);
}
