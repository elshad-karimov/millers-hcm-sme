package az.millers.hcm.leave.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.leave.domain.LeaveEncashment;

public interface LeaveEncashmentRepository extends JpaRepository<LeaveEncashment, UUID> {
    List<LeaveEncashment> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);
    List<LeaveEncashment> findByEmployeeIdAndLeaveTypeIdAndYear(UUID employeeId, UUID leaveTypeId, int year);
}
