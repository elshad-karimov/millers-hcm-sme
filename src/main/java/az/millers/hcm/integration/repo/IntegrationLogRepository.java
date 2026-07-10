package az.millers.hcm.integration.repo;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import az.millers.hcm.integration.domain.IntegrationLog;
import az.millers.hcm.integration.domain.IntegrationStatus;

@Repository
public interface IntegrationLogRepository extends JpaRepository<IntegrationLog, UUID> {

    List<IntegrationLog> findByConfigIdOrderByRunAtDesc(UUID configId, Pageable pageable);

    List<IntegrationLog> findByTenantIdAndStatusOrderByRunAtDesc(String tenantId, IntegrationStatus status, Pageable pageable);

    @Modifying
    @Query("DELETE FROM IntegrationLog l WHERE l.runAt < :cutoff")
    int deleteOlderThan(OffsetDateTime cutoff);
}
