package az.millers.hcm.notifications.repo;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import az.millers.hcm.notifications.domain.NotificationChannel;
import az.millers.hcm.notifications.domain.NotificationLog;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    Page<NotificationLog> findByRecipientOrderByCreatedAtDesc(String recipient, Pageable pageable);

    long countByRecipientAndReadAtIsNull(String recipient);

    @Modifying
    @Query("UPDATE NotificationLog n SET n.readAt = CURRENT_TIMESTAMP " +
           "WHERE n.recipient = :recipient AND n.readAt IS NULL")
    int markAllReadByRecipient(@Param("recipient") String recipient);

    /**
     * Delivery-audit view over the canonical log. Status is derived from the
     * timestamps: a row with {@code failed_at} set is FAILED, otherwise SENT.
     * Callers pass {@code sentOnly}/{@code failedOnly} (both false = no status
     * filter). Ordered newest-first by {@code created_at}.
     */
    @Query("SELECT n FROM NotificationLog n WHERE " +
           "(:channel IS NULL OR n.channel = :channel) AND " +
           "(:failedOnly = false OR n.failedAt IS NOT NULL) AND " +
           "(:sentOnly = false OR n.failedAt IS NULL) AND " +
           "(:from IS NULL OR n.createdAt >= :from) AND " +
           "(:to IS NULL OR n.createdAt <= :to) " +
           "ORDER BY n.createdAt DESC")
    List<NotificationLog> findDeliveries(@Param("channel") NotificationChannel channel,
                                         @Param("sentOnly") boolean sentOnly,
                                         @Param("failedOnly") boolean failedOnly,
                                         @Param("from") OffsetDateTime from,
                                         @Param("to") OffsetDateTime to,
                                         Pageable pageable);
}
