package az.millers.hcm.compbenefits.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.compbenefits.domain.AllowanceStatus;
import az.millers.hcm.compbenefits.domain.EmployeeAllowance;

public interface EmployeeAllowanceRepository extends JpaRepository<EmployeeAllowance, UUID> {

    @org.springframework.data.jpa.repository.Query(
        value = "SELECT nextval('comp_benefits.allowance_no_seq')", nativeQuery = true)
    long nextNoSequence();

    List<EmployeeAllowance> findByEmployeeIdOrderByEffectiveFromDesc(UUID employeeId);

    List<EmployeeAllowance> findByAllowanceTypeIdOrderByEffectiveFromDesc(UUID allowanceTypeId);

    @Query("""
           select a from EmployeeAllowance a
           where a.status = :status
             and a.effectiveFrom <= :on
             and (a.effectiveTo is null or a.effectiveTo >= :on)
           order by a.employeeId asc, a.allowanceTypeId asc
           """)
    List<EmployeeAllowance> findActiveOn(AllowanceStatus status, LocalDate on);

    @Query("""
           select a from EmployeeAllowance a
           where a.employeeId = :employeeId
             and a.status = :status
             and a.effectiveFrom <= :on
             and (a.effectiveTo is null or a.effectiveTo >= :on)
           order by a.allowanceTypeId asc
           """)
    List<EmployeeAllowance> findActiveForEmployeeOn(UUID employeeId, AllowanceStatus status, LocalDate on);
}
