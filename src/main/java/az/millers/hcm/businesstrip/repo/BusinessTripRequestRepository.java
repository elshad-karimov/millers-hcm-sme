package az.millers.hcm.businesstrip.repo;

import java.util.Collection;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import az.millers.hcm.businesstrip.domain.BusinessTripRequest;
import az.millers.hcm.businesstrip.domain.TripStatus;

public interface BusinessTripRequestRepository extends JpaRepository<BusinessTripRequest, UUID> {

    @Query(value = "SELECT config.next_tenant_seq('business_trip.trip_no_seq')", nativeQuery = true)
    long nextTripNoSequence();

    Page<BusinessTripRequest> findByEmployeeIdOrderByStartDateDesc(UUID employeeId, Pageable pageable);

    Page<BusinessTripRequest> findByStatusOrderByStartDateDesc(TripStatus status, Pageable pageable);

    Page<BusinessTripRequest> findAllByOrderByStartDateDesc(Pageable pageable);

    /** Scope-bounded equivalents used by ABAC-filtered lists (PRD 14.9). */
    Page<BusinessTripRequest> findByEmployeeIdInOrderByStartDateDesc(
            Collection<UUID> employeeIds, Pageable pageable);

    Page<BusinessTripRequest> findByEmployeeIdInAndStatusOrderByStartDateDesc(
            Collection<UUID> employeeIds, TripStatus status, Pageable pageable);

    @Query("""
            select t from BusinessTripRequest t
            where t.employeeId = :employeeId
              and t.status in (
                  az.millers.hcm.businesstrip.domain.TripStatus.APPROVED,
                  az.millers.hcm.businesstrip.domain.TripStatus.COMPLETED)
              and t.startDate <= :rangeEnd
              and t.endDate   >= :rangeStart
            """)
    java.util.List<BusinessTripRequest> findApprovedOverlapping(
            UUID employeeId, java.time.LocalDate rangeStart, java.time.LocalDate rangeEnd);
}
