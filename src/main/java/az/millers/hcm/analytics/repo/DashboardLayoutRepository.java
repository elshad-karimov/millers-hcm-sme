package az.millers.hcm.analytics.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import az.millers.hcm.analytics.domain.DashboardLayout;

/**
 * M474 — Dashboard layout repository.
 */
public interface DashboardLayoutRepository extends JpaRepository<DashboardLayout, UUID> {

    List<DashboardLayout> findByTenantIdAndOwnerUsernameOrderByCreatedAtDesc(String tenantId, String ownerUsername);

    @Query("SELECT d FROM DashboardLayout d WHERE d.tenantId = :tenantId " +
           "AND (d.ownerUsername = :username OR d.shared = true) ORDER BY d.createdAt DESC")
    List<DashboardLayout> findOwnedOrShared(String tenantId, String username);
}
