package az.millers.hcm.payroll.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.payroll.domain.ComponentKind;
import az.millers.hcm.payroll.domain.SalaryComponent;

/**
 * M349 — Salary component catalog repository.
 */
public interface SalaryComponentRepository extends JpaRepository<SalaryComponent, UUID> {

    @Query("SELECT c FROM SalaryComponent c WHERE c.tenantId = 'default' "
            + "AND (:kind IS NULL OR c.kind = :kind) "
            + "AND (:isActive IS NULL OR c.isActive = :isActive) "
            + "AND (:isStatutory IS NULL OR c.isStatutory = :isStatutory) "
            + "ORDER BY c.code")
    Page<SalaryComponent> findAll(ComponentKind kind, Boolean isActive, Boolean isStatutory, Pageable pageable);

    Optional<SalaryComponent> findByTenantIdAndCode(String tenantId, String code);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN TRUE ELSE FALSE END FROM SalaryComponentAssignment a "
            + "WHERE a.component.id = :componentId AND a.effectiveTo IS NULL")
    boolean hasActiveAssignments(UUID componentId);
}
