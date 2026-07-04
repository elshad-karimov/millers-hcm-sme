package az.millers.hcm.payroll.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.payroll.domain.SalaryComponentAssignment;

/**
 * M349 — Per-employee salary component assignment repository.
 */
public interface SalaryComponentAssignmentRepository extends JpaRepository<SalaryComponentAssignment, UUID> {

    @Query("SELECT a FROM SalaryComponentAssignment a JOIN FETCH a.component "
            + "WHERE a.tenantId = 'default' AND a.employeeId = :employeeId "
            + "AND a.effectiveFrom <= :date "
            + "AND (a.effectiveTo IS NULL OR a.effectiveTo >= :date)")
    List<SalaryComponentAssignment> findActiveOn(UUID employeeId, LocalDate date);

    @Query("SELECT a FROM SalaryComponentAssignment a JOIN FETCH a.component "
            + "WHERE a.tenantId = 'default' AND a.employeeId = :employeeId "
            + "AND (:activeOnly = false OR a.effectiveTo IS NULL) "
            + "ORDER BY a.effectiveFrom DESC")
    List<SalaryComponentAssignment> findByEmployeeId(UUID employeeId, boolean activeOnly);

    @Query("SELECT a FROM SalaryComponentAssignment a "
            + "WHERE a.tenantId = 'default' AND a.employeeId = :employeeId "
            + "AND a.component.id = :componentId AND a.effectiveTo IS NULL")
    List<SalaryComponentAssignment> findOpenByEmployeeAndComponent(UUID employeeId, UUID componentId);
}
