package az.millers.hcm.payroll.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.payroll.domain.GLAccountMapping;
import az.millers.hcm.payroll.domain.GLAccountType;

public interface GLAccountMappingRepository extends JpaRepository<GLAccountMapping, UUID> {

    List<GLAccountMapping> findByTenantIdAndIsActiveTrueOrderByComponentKindAscComponentCodeAsc(
            String tenantId);

    @Query("SELECT m FROM GLAccountMapping m " +
           "WHERE m.tenantId = :tenantId " +
           "AND m.componentKind = :kind " +
           "AND m.componentCode = :code " +
           "AND m.accountType = :accountType " +
           "AND m.isActive = true")
    Optional<GLAccountMapping> findExactMatch(String tenantId, String kind, String code,
                                                GLAccountType accountType);

    @Query("SELECT m FROM GLAccountMapping m " +
           "WHERE m.tenantId = :tenantId " +
           "AND m.componentKind = :kind " +
           "AND m.componentCode IS NULL " +
           "AND m.accountType = :accountType " +
           "AND m.isActive = true")
    Optional<GLAccountMapping> findKindFallback(String tenantId, String kind,
                                                  GLAccountType accountType);
}
