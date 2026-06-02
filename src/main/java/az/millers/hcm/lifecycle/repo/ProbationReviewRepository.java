package az.millers.hcm.lifecycle.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.lifecycle.domain.ProbationReview;
import az.millers.hcm.lifecycle.domain.ProbationReviewStatus;

public interface ProbationReviewRepository
        extends JpaRepository<ProbationReview, UUID> {

    /** Most-recent-first list for the employee's profile tab. */
    List<ProbationReview> findByEmployeeIdOrderByScheduledDateDesc(UUID employeeId);

    /** All reviews attached to a given contract — used during cancel-on-rescind. */
    List<ProbationReview> findByContractId(UUID contractId);

    /** Outstanding reviews to cancel when probation ends early. */
    List<ProbationReview> findByContractIdAndStatus(UUID contractId, ProbationReviewStatus status);

    /**
     * Used by {@code ProbationReviewExpirySource} — only SCHEDULED reviews
     * generate reminder alerts; COMPLETED / CANCELLED are filtered out
     * by the partial index in V59.
     */
    @Query("""
            select r from ProbationReview r
            where r.scheduledDate = :date
              and r.status = 'SCHEDULED'
            """)
    List<ProbationReview> findScheduledOn(LocalDate date);

    /**
     * Pending probation reviews scheduled on or before {@code date} for the
     * given employees. Used by the manager team dashboard (M76 / P2-11/12) to
     * flag overdue / due-soon reviews on direct reports.
     */
    @Query("""
            select r from ProbationReview r
            where r.employeeId in :employeeIds
              and r.scheduledDate <= :date
              and r.status = 'SCHEDULED'
            order by r.scheduledDate asc
            """)
    List<ProbationReview> findPendingForEmployees(
            java.util.Collection<UUID> employeeIds, LocalDate date);
}
