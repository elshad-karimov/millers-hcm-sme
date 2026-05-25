package az.millers.hcm.notifications.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.notifications.domain.DeviceToken;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, UUID> {

    List<DeviceToken> findByUsername(String username);

    void deleteByUsernameAndFcmToken(String username, String fcmToken);
}
