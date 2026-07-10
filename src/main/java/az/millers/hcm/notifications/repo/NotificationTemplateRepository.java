package az.millers.hcm.notifications.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import az.millers.hcm.notifications.domain.NotificationChannel;
import az.millers.hcm.notifications.domain.NotificationTemplate;

@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {

    List<NotificationTemplate> findByTenantIdOrderByNameAsc(String tenantId);

    Optional<NotificationTemplate> findByIdAndTenantId(UUID id, String tenantId);

    Optional<NotificationTemplate> findByCodeAndTenantId(String code, String tenantId);

    List<NotificationTemplate> findByTenantIdAndChannelAndActiveOrderByNameAsc(
        String tenantId, NotificationChannel channel, boolean active);
}
