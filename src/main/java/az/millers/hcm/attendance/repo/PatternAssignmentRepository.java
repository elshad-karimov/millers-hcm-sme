package az.millers.hcm.attendance.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import az.millers.hcm.attendance.domain.PatternAssignment;

public interface PatternAssignmentRepository extends JpaRepository<PatternAssignment, UUID> {

    List<PatternAssignment> findByEmployeeIdOrderByStartDateDesc(UUID employeeId);

    List<PatternAssignment> findByPatternIdOrderByStartDateDesc(UUID patternId);

    long countByPatternId(UUID patternId);

    /**
     * The single open assignment for an employee, if any. Used by the
     * roster generator when expanding "no end_date" rotations into roster
     * rows.
     */
    @Query("select p from PatternAssignment p "
            + "where p.employeeId = :employeeId and p.endDate is null")
    Optional<PatternAssignment> findOpenForEmployee(@Param("employeeId") UUID employeeId);

    /**
     * Every assignment that overlaps any portion of [{@code from}, {@code to}].
     * Open-ended assignments (endDate IS NULL) overlap when their startDate is
     * on or before {@code to}.
     */
    @Query("""
            select p from PatternAssignment p
            where p.startDate <= :to
              and (p.endDate is null or p.endDate >= :from)
              and (:employeeIds is null or p.employeeId in :employeeIds)
            order by p.employeeId asc, p.startDate asc
            """)
    List<PatternAssignment> findOverlapping(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("employeeIds") java.util.Collection<UUID> employeeIds);
}
