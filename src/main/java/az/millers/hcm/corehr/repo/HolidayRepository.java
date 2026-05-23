package az.millers.hcm.corehr.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import az.millers.hcm.corehr.domain.Holiday;

public interface HolidayRepository extends JpaRepository<Holiday, UUID> {

    Optional<Holiday> findByJurisdictionAndHolidayDate(String jurisdiction, LocalDate holidayDate);

    /** Hot-path lookup for the timesheet / leave engines. Active rows only. */
    @Query("""
            select h from Holiday h
            where h.active = true
              and h.jurisdiction = :jurisdiction
              and h.holidayDate between :start and :end
            order by h.holidayDate
            """)
    List<Holiday> activeBetween(@Param("jurisdiction") String jurisdiction,
                                 @Param("start") LocalDate start,
                                 @Param("end") LocalDate end);

    @Query("""
            select h from Holiday h
            where h.jurisdiction = :jurisdiction
            order by h.holidayDate
            """)
    List<Holiday> findAllForJurisdiction(@Param("jurisdiction") String jurisdiction);
}
