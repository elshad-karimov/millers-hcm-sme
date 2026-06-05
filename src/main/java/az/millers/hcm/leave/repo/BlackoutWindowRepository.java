package az.millers.hcm.leave.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import az.millers.hcm.leave.domain.BlackoutWindow;

public interface BlackoutWindowRepository extends JpaRepository<BlackoutWindow, UUID> {

    /**
     * Active windows whose date range overlaps {@code [from, to]}.
     * Overlap math: NOT (end &lt; from OR start &gt; to). Includes every
     * scope; the service filters by employee org-unit and leave-type
     * applicability with pure-static
     * {@link az.millers.hcm.leave.service.BlackoutChecker}.
     */
    @Query("""
        SELECT b FROM BlackoutWindow b
         WHERE b.active = true
           AND NOT (b.endDate < :from OR b.startDate > :to)
         ORDER BY b.startDate
        """)
    List<BlackoutWindow> findActiveOverlapping(@Param("from") LocalDate from,
                                                @Param("to") LocalDate to);

    List<BlackoutWindow> findAllByOrderByStartDateDesc();
}
