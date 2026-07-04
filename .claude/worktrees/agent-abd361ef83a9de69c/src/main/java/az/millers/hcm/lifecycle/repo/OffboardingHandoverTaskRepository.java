package az.millers.hcm.lifecycle.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.lifecycle.domain.OffboardingHandoverTask;

public interface OffboardingHandoverTaskRepository extends JpaRepository<OffboardingHandoverTask, UUID> {

    List<OffboardingHandoverTask> findByCaseIdOrderByCreatedAt(UUID caseId);
}
