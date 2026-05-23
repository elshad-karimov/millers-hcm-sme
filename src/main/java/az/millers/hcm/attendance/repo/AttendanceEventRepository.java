package az.millers.hcm.attendance.repo;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.attendance.domain.AttendanceEvent;

public interface AttendanceEventRepository extends JpaRepository<AttendanceEvent, UUID> {

    List<AttendanceEvent> findByEmployeeIdAndEventTimeBetweenOrderByEventTimeAsc(
            UUID employeeId, OffsetDateTime from, OffsetDateTime to);

    Page<AttendanceEvent> findByEventTimeBetween(OffsetDateTime from, OffsetDateTime to, Pageable pageable);

    Page<AttendanceEvent> findByEmployeeIdAndEventTimeBetween(
            UUID employeeId, OffsetDateTime from, OffsetDateTime to, Pageable pageable);
}
