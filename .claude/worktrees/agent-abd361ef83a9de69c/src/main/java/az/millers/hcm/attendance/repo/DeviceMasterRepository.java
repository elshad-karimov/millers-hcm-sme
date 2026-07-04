package az.millers.hcm.attendance.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.attendance.domain.DeviceMaster;

public interface DeviceMasterRepository extends JpaRepository<DeviceMaster, UUID> {

    List<DeviceMaster> findByTenantIdOrderByCodeAsc(UUID tenantId);

    Optional<DeviceMaster> findByTenantIdAndCode(UUID tenantId, String code);

    Optional<DeviceMaster> findByIdAndTenantId(UUID id, UUID tenantId);
}
