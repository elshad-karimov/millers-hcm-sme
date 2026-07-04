package az.millers.hcm.attendance.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.attendance.domain.ExceptionConfig;

public interface ExceptionConfigRepository extends JpaRepository<ExceptionConfig, UUID> {

    List<ExceptionConfig> findByTenantIdAndEnabledTrue(UUID tenantId);

    Optional<ExceptionConfig> findByTenantIdAndExceptionType(UUID tenantId, String exceptionType);

    List<ExceptionConfig> findByTenantIdOrderByExceptionTypeAsc(UUID tenantId);
}
