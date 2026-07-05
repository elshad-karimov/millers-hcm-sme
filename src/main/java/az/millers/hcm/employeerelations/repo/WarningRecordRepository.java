package az.millers.hcm.employeerelations.repo;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import az.millers.hcm.employeerelations.domain.WarningRecord;

/**
 * M446 — Warning record repository.
 */
public interface WarningRecordRepository extends JpaRepository<WarningRecord, UUID> {

    Optional<WarningRecord> findByIdAndTenantId(UUID id, String tenantId);

    List<WarningRecord> findByTenantIdAndEmployeeIdOrderByIssuedAtDesc(String tenantId, UUID employeeId);

    @Query("SELECT w FROM WarningRecord w WHERE w.tenantId = :tenantId AND w.employeeId = :employeeId " +
           "AND (w.expiresAt IS NULL OR w.expiresAt > :now)")
    List<WarningRecord> findActiveWarnings(@Param("tenantId") String tenantId,
                                          @Param("employeeId") UUID employeeId,
                                          @Param("now") OffsetDateTime now);
}
