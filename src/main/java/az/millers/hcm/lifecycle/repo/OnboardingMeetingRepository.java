package az.millers.hcm.lifecycle.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.lifecycle.domain.OnboardingMeeting;

public interface OnboardingMeetingRepository extends JpaRepository<OnboardingMeeting, UUID> {
    List<OnboardingMeeting> findByEmployeeIdOrderByStartsAtAsc(UUID employeeId);
    Optional<OnboardingMeeting> findByTaskStatusId(UUID taskStatusId);
}
