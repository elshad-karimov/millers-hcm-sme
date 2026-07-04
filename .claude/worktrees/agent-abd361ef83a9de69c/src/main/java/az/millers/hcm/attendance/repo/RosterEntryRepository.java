package az.millers.hcm.attendance.repo;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import az.millers.hcm.attendance.domain.RosterEntry;

public interface RosterEntryRepository extends JpaRepository<RosterEntry, UUID> {

    Optional<RosterEntry> findByEmployeeIdAndRosterDate(UUID employeeId, LocalDate rosterDate);

    List<RosterEntry> findByEmployeeIdAndRosterDateBetweenOrderByRosterDateAsc(
            UUID employeeId, LocalDate from, LocalDate to);

    List<RosterEntry> findByRosterDateBetweenOrderByRosterDateAscEmployeeIdAsc(
            LocalDate from, LocalDate to);

    /**
     * Fetch the roster window for a specific group of employees in one query.
     * Used by the weekly roster grid so it doesn't issue one query per row.
     */
    @Query("""
            select r from RosterEntry r
            where r.employeeId in :employeeIds
              and r.rosterDate between :from and :to
            order by r.rosterDate asc, r.employeeId asc
            """)
    List<RosterEntry> findForEmployeesBetween(
            @Param("employeeIds") Collection<UUID> employeeIds,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    long countByShiftId(UUID shiftId);
}
