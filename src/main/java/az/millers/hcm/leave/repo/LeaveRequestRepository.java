package az.millers.hcm.leave.repo;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.leave.domain.LeaveRequest;
import az.millers.hcm.leave.domain.LeaveRequestStatus;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID> {

    @Query(value = "SELECT nextval('leave_mgmt.leave_request_no_seq')", nativeQuery = true)
    long nextRequestNoSequence();

    Page<LeaveRequest> findByEmployeeIdOrderByStartDateDesc(UUID employeeId, Pageable pageable);

    Page<LeaveRequest> findByStatusOrderByStartDateDesc(LeaveRequestStatus status, Pageable pageable);

    Page<LeaveRequest> findAllByOrderByStartDateDesc(Pageable pageable);

    /** Scope-bounded equivalents used by ABAC-filtered lists (PRD 14.9). */
    Page<LeaveRequest> findByEmployeeIdInOrderByStartDateDesc(
            Collection<UUID> employeeIds, Pageable pageable);

    Page<LeaveRequest> findByEmployeeIdInAndStatusOrderByStartDateDesc(
            Collection<UUID> employeeIds, LeaveRequestStatus status, Pageable pageable);

    List<LeaveRequest> findByEmployeeIdAndStartDateBetween(
            UUID employeeId, LocalDate from, LocalDate to);

    @Query("""
            select r from LeaveRequest r
            where r.employeeId = :employeeId
              and r.status = az.millers.hcm.leave.domain.LeaveRequestStatus.APPROVED
              and r.startDate <= :rangeEnd
              and r.endDate >= :rangeStart
            """)
    List<LeaveRequest> findApprovedOverlapping(UUID employeeId, LocalDate rangeStart, LocalDate rangeEnd);
}
