package az.millers.hcm.payroll.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.payroll.domain.CostCenterAllocation;

public interface CostCenterAllocationRepository extends JpaRepository<CostCenterAllocation, UUID> {

    @Query("SELECT c FROM CostCenterAllocation c " +
           "WHERE c.tenantId = :tenantId AND c.employeeId = :employeeId " +
           "AND c.effectiveFrom <= :asOf " +
           "AND (c.effectiveTo IS NULL OR c.effectiveTo >= :asOf) " +
           "ORDER BY c.effectiveFrom DESC")
    List<CostCenterAllocation> findActiveOn(String tenantId, UUID employeeId, LocalDate asOf);

    List<CostCenterAllocation> findByTenantIdAndEmployeeIdOrderByEffectiveFromDesc(
            String tenantId, UUID employeeId);
}
