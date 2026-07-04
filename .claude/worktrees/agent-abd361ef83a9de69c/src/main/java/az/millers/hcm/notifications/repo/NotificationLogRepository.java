package az.millers.hcm.notifications.repo;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import az.millers.hcm.notifications.domain.NotificationLog;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    Page<NotificationLog> findByRecipientOrderByCreatedAtDesc(String recipient, Pageable pageable);

    long countByRecipientAndReadAtIsNull(String recipient);

    @Modifying
    @Query("UPDATE NotificationLog n SET n.readAt = CURRENT_TIMESTAMP " +
           "WHERE n.recipient = :recipient AND n.readAt IS NULL")
    int markAllReadByRecipient(@Param("recipient") String recipient);
}
