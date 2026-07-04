package az.millers.hcm.performance.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.performance.domain.EmployeeTalentPool;

public interface EmployeeTalentPoolRepository extends JpaRepository<EmployeeTalentPool, UUID> {

    List<EmployeeTalentPool> findByTenantIdOrderByCodeAsc(String tenantId);

    List<EmployeeTalentPool> findByTenantIdAndActiveTrueOrderByCodeAsc(String tenantId);

    Optional<EmployeeTalentPool> findByTenantIdAndCode(String tenantId, String code);

    boolean existsByTenantIdAndCode(String tenantId, String code);
}
