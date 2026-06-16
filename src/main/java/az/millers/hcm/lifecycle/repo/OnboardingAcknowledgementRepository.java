package az.millers.hcm.lifecycle.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.lifecycle.domain.OnboardingAcknowledgement;

public interface OnboardingAcknowledgementRepository
        extends JpaRepository<OnboardingAcknowledgement, UUID> {

    boolean existsByTaskStatusId(UUID taskStatusId);

    List<OnboardingAcknowledgement> findByEmployeeIdOrderByAcknowledgedAtDesc(UUID employeeId);
}
