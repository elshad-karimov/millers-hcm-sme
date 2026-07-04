package az.millers.hcm.corehr.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.corehr.domain.EmployeeStatusHistory;

public interface EmployeeStatusHistoryRepository
        extends JpaRepository<EmployeeStatusHistory, UUID> {

    List<EmployeeStatusHistory> findByEmployeeIdOrderByEffectiveFromDesc(UUID employeeId);

    @Query("""
            select h from EmployeeStatusHistory h
            where h.employeeId = :employeeId
              and h.effectiveTo is null
            """)
    Optional<EmployeeStatusHistory> findOpenForEmployee(UUID employeeId);

    @Query("""
            select h from EmployeeStatusHistory h
            where h.employeeId = :employeeId
              and h.effectiveFrom <= :date
              and (h.effectiveTo is null or h.effectiveTo >= :date)
            order by h.effectiveFrom desc
            """)
    Optional<EmployeeStatusHistory> findActiveOn(UUID employeeId, LocalDate date);
}
