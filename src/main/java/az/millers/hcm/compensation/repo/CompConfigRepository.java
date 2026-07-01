package az.millers.hcm.compensation.repo;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.compensation.domain.CompConfig;

public interface CompConfigRepository extends JpaRepository<CompConfig, UUID> {

    Optional<CompConfig> findByTenantId(String tenantId);
}
