package az.millers.hcm.performance.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.performance.domain.MentorProfile;

public interface MentorProfileRepository extends JpaRepository<MentorProfile, UUID> {
    List<MentorProfile> findByTenantIdAndActiveTrueOrderByCreatedAtDesc(String tenantId);
    Optional<MentorProfile> findByTenantIdAndEmployeeId(String tenantId, UUID employeeId);
}
