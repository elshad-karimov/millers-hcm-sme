package az.millers.hcm.leave.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.leave.domain.LeaveBalance;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, UUID> {

    Optional<LeaveBalance> findByEmployeeIdAndLeaveTypeIdAndYear(
            UUID employeeId, UUID leaveTypeId, int year);

    List<LeaveBalance> findByEmployeeIdAndYearOrderByLeaveTypeId(UUID employeeId, int year);

    List<LeaveBalance> findByYearOrderByEmployeeIdAscLeaveTypeIdAsc(int year);
}
