package az.millers.hcm.recruitment.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.recruitment.domain.ApplicationEvent;

public interface ApplicationEventRepository extends JpaRepository<ApplicationEvent, UUID> {

    List<ApplicationEvent> findByApplicationIdOrderByCreatedAtAsc(UUID applicationId);
}
