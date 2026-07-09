package az.millers.hcm.lifecycle.repo;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import az.millers.hcm.lifecycle.domain.AssetDamageLossCase;
import az.millers.hcm.lifecycle.domain.AssetDamageLossCaseStatus;

@Repository
public interface AssetDamageLossCaseRepository extends JpaRepository<AssetDamageLossCase, UUID> {
    List<AssetDamageLossCase> findByTenantIdAndStatusOrderByReportedAtDesc(String tenantId, AssetDamageLossCaseStatus status);
    List<AssetDamageLossCase> findByTenantIdOrderByReportedAtDesc(String tenantId);
    List<AssetDamageLossCase> findByEmployeeIdOrderByReportedAtDesc(UUID employeeId);
}
