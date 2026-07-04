package az.millers.hcm.attendance.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.attendance.domain.ScheduleAssignment;

public interface ScheduleAssignmentRepository extends JpaRepository<ScheduleAssignment, UUID> {

    List<ScheduleAssignment> findByEmployeeIdOrderByEffectiveFromDesc(UUID employeeId);

    @Query("""
            select a from ScheduleAssignment a
            where a.employeeId = :employeeId
              and a.effectiveFrom <= :date
              and (a.effectiveTo is null or a.effectiveTo >= :date)
            order by a.effectiveFrom desc
            """)
    Optional<ScheduleAssignment> findActiveOn(UUID employeeId, LocalDate date);

    @Query("""
            select distinct a.employeeId from ScheduleAssignment a
            where a.effectiveFrom <= :date
              and (a.effectiveTo is null or a.effectiveTo >= :date)
            """)
    List<UUID> findEmployeesWithScheduleOn(LocalDate date);
}
