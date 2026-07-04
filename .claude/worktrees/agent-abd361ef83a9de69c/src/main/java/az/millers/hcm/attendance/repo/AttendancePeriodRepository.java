package az.millers.hcm.attendance.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.attendance.domain.AttendancePeriod;

public interface AttendancePeriodRepository extends JpaRepository<AttendancePeriod, UUID> {

    Optional<AttendancePeriod> findByTenantIdAndYearAndMonth(UUID tenantId, int year, int month);

    List<AttendancePeriod> findByTenantIdOrderByYearDescMonthDesc(UUID tenantId);
}
