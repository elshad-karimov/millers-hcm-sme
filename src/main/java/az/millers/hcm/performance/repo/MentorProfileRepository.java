package az.millers.hcm.performance.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import az.millers.hcm.performance.domain.MentorProfile;

public interface MentorProfileRepository extends JpaRepository<MentorProfile, UUID> {
    List<MentorProfile> findByTenantIdAndActiveTrueOrderByCreatedAtDesc(String tenantId);
    Optional<MentorProfile> findByTenantIdAndEmployeeId(String tenantId, UUID employeeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM MentorProfile p WHERE p.id = :id")
    Optional<MentorProfile> lockById(@Param("id") UUID id);
}
