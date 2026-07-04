package az.millers.hcm.learning.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.learning.domain.TrainingAttendance;

public interface TrainingAttendanceRepository extends JpaRepository<TrainingAttendance, UUID> {

    List<TrainingAttendance> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    List<TrainingAttendance> findByTenantIdAndEmployeeIdOrderByCreatedAtDesc(String tenantId, UUID employeeId);

    boolean existsBySessionIdAndEmployeeId(UUID sessionId, UUID employeeId);

    long countBySessionIdAndStatusIn(UUID sessionId, List<String> statuses);
}
