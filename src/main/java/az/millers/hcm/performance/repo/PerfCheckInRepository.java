package az.millers.hcm.performance.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.performance.domain.PerfCheckIn;

public interface PerfCheckInRepository extends JpaRepository<PerfCheckIn, UUID> {

    List<PerfCheckIn> findByTenantIdAndEmployeeIdOrderByMeetingDateDesc(String tenantId, UUID employeeId);
}
