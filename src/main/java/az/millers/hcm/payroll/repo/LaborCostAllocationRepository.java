package az.millers.hcm.payroll.repo;

import az.millers.hcm.payroll.domain.LaborCostAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * M485: Labor cost allocation repository.
 */
@Repository
public interface LaborCostAllocationRepository extends JpaRepository<LaborCostAllocation, UUID> {

    List<LaborCostAllocation> findByTenantIdAndWorkDateBetweenOrderByWorkDate(
        String tenantId, LocalDate from, LocalDate to);

    List<LaborCostAllocation> findByTenantIdAndProjectIdAndWorkDateBetween(
        String tenantId, UUID projectId, LocalDate from, LocalDate to);

    List<LaborCostAllocation> findByTenantIdAndCostCenterIdAndWorkDateBetween(
        String tenantId, UUID costCenterId, LocalDate from, LocalDate to);

    void deleteByTimesheetDayIdIn(List<UUID> timesheetDayIds);
}
