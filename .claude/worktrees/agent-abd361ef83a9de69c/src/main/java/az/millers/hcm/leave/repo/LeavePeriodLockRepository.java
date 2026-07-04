package az.millers.hcm.leave.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import az.millers.hcm.leave.domain.LeavePeriodLock;

public interface LeavePeriodLockRepository extends JpaRepository<LeavePeriodLock, UUID> {

    List<LeavePeriodLock> findAllByOrderByPeriodStartDesc();

    /**
     * Returns active locks that overlap the given date range AND either apply
     * to all leave types (leaveTypeId IS NULL) or to the specific type.
     */
    @Query("""
        SELECT l FROM LeavePeriodLock l
        WHERE l.active = true
          AND l.periodStart <= :endDate
          AND l.periodEnd   >= :startDate
          AND (l.leaveTypeId IS NULL OR l.leaveTypeId = :leaveTypeId)
        """)
    List<LeavePeriodLock> findActiveOverlapping(
            @Param("startDate")   LocalDate startDate,
            @Param("endDate")     LocalDate endDate,
            @Param("leaveTypeId") UUID leaveTypeId);
}
