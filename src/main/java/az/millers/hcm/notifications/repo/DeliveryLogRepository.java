package az.millers.hcm.notifications.repo;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import az.millers.hcm.notifications.domain.DeliveryLog;
import az.millers.hcm.notifications.domain.DeliveryStatus;
import az.millers.hcm.notifications.domain.NotificationChannel;

@Repository
public interface DeliveryLogRepository extends JpaRepository<DeliveryLog, UUID> {

    @Query("SELECT d FROM DeliveryLog d WHERE d.tenantId = :tenantId " +
           "AND (:channel IS NULL OR d.channel = :channel) " +
           "AND (:status IS NULL OR d.status = :status) " +
           "AND (:from IS NULL OR d.sentAt >= :from) " +
           "AND (:to IS NULL OR d.sentAt <= :to) " +
           "ORDER BY d.sentAt DESC")
    List<DeliveryLog> findFiltered(String tenantId, NotificationChannel channel,
                                    DeliveryStatus status, OffsetDateTime from,
                                    OffsetDateTime to, Pageable pageable);
}
