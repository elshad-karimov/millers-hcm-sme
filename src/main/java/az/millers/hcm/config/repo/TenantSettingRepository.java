package az.millers.hcm.config.repo;

import az.millers.hcm.config.domain.TenantSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TenantSettingRepository extends JpaRepository<TenantSetting, TenantSetting.TenantSettingId> {
    Optional<TenantSetting> findByTenantIdAndKey(String tenantId, String key);
    List<TenantSetting> findByTenantId(String tenantId);
}
