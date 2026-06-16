package az.millers.hcm.lifecycle.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.lifecycle.domain.BuddyRole;
import az.millers.hcm.lifecycle.domain.BuddyStatus;
import az.millers.hcm.lifecycle.domain.OnboardingBuddy;

public interface OnboardingBuddyRepository extends JpaRepository<OnboardingBuddy, UUID> {

    List<OnboardingBuddy> findByEmployeeIdOrderByAssignedAtDesc(UUID employeeId);

    List<OnboardingBuddy> findByBuddyEmployeeIdAndStatusOrderByAssignedAtDesc(UUID buddyEmployeeId, BuddyStatus status);

    Optional<OnboardingBuddy> findByEmployeeIdAndRoleAndStatus(UUID employeeId, BuddyRole role, BuddyStatus status);
}
