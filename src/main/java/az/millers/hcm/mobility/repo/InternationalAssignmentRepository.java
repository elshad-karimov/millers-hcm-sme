package az.millers.hcm.mobility.repo;

import az.millers.hcm.mobility.domain.InternationalAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InternationalAssignmentRepository extends JpaRepository<InternationalAssignment, UUID> {
    List<InternationalAssignment> findByTenantIdOrderByStartDateDesc(String tenantId);
    List<InternationalAssignment> findByTenantIdAndStatusOrderByStartDateDesc(String tenantId, String status);
    Optional<InternationalAssignment> findByIdAndTenantId(UUID id, String tenantId);
    List<InternationalAssignment> findByTenantIdAndEmployeeIdOrderByStartDateDesc(String tenantId, UUID employeeId);

    @Query("SELECT a FROM InternationalAssignment a WHERE a.tenantId = :tenantId AND a.visaExpiry <= :expiryDate AND a.status = 'ACTIVE'")
    List<InternationalAssignment> findExpiringVisas(String tenantId, LocalDate expiryDate);
}
