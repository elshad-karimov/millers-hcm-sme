package az.millers.hcm.notifications.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.notifications.domain.NotificationCategory;
import az.millers.hcm.notifications.domain.NotificationChannel;
import az.millers.hcm.notifications.domain.NotificationPreference;

public interface NotificationPreferenceRepository
        extends JpaRepository<NotificationPreference, UUID> {

    List<NotificationPreference> findByUsername(String username);

    Optional<NotificationPreference> findByUsernameAndCategoryAndChannel(
            String username, NotificationCategory category, NotificationChannel channel);
}
