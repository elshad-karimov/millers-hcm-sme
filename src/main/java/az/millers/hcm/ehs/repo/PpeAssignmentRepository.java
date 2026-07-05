package az.millers.hcm.ehs.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import az.millers.hcm.ehs.domain.PpeAssignment;

public interface PpeAssignmentRepository extends JpaRepository<PpeAssignment, UUID> {

    Optional<PpeAssignment> findByIdAndTenantId(UUID id, String tenantId);

    List<PpeAssignment> findByTenantIdAndEmployeeIdOrderByIssuedAtDesc(String tenantId, UUID employeeId);

    List<PpeAssignment> findByTenantIdOrderByIssuedAtDesc(String tenantId);

    @Query("SELECT a FROM PpeAssignment a WHERE a.tenantId = :tenant " +
           "AND a.returnedAt IS NULL AND a.expiryDate BETWEEN :from AND :to")
    List<PpeAssignment> findExpiringAssignments(@Param("tenant") String tenantId,
                                                 @Param("from") LocalDate from,
                                                 @Param("to") LocalDate to);
}
