package az.millers.hcm.corehr.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.corehr.domain.EmployeeEmploymentHistory;

public interface EmployeeEmploymentHistoryRepository
        extends JpaRepository<EmployeeEmploymentHistory, UUID> {

    /** Most-recent first — for the Timeline / Lifecycle History tab. */
    List<EmployeeEmploymentHistory> findByEmployeeIdOrderByEffectiveFromDesc(UUID employeeId);

    /** The currently-open slice (effective_to IS NULL) — fast via partial unique index. */
    @Query("""
            select h from EmployeeEmploymentHistory h
            where h.employeeId = :employeeId
              and h.effectiveTo is null
            """)
    Optional<EmployeeEmploymentHistory> findOpenForEmployee(UUID employeeId);

    /** State on a given date — for retroactive reports. */
    @Query("""
            select h from EmployeeEmploymentHistory h
            where h.employeeId = :employeeId
              and h.effectiveFrom <= :date
              and (h.effectiveTo is null or h.effectiveTo >= :date)
            order by h.effectiveFrom desc
            """)
    Optional<EmployeeEmploymentHistory> findActiveOn(UUID employeeId, LocalDate date);
}
