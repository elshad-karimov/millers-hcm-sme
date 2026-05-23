package az.millers.hcm.payroll.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.payroll.domain.EmployeeCompensation;

public interface EmployeeCompensationRepository extends JpaRepository<EmployeeCompensation, UUID> {

    List<EmployeeCompensation> findByEmployeeIdOrderByEffectiveFromDesc(UUID employeeId);

    @Query("""
            select c from EmployeeCompensation c
            where c.employeeId = :employeeId
              and c.effectiveFrom <= :date
              and (c.effectiveTo is null or c.effectiveTo >= :date)
            order by c.effectiveFrom desc
            """)
    Optional<EmployeeCompensation> findActiveOn(UUID employeeId, LocalDate date);
}
