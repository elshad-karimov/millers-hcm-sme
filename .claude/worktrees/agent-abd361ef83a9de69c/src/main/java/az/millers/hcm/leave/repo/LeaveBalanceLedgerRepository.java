package az.millers.hcm.leave.repo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import az.millers.hcm.leave.domain.LeaveBalanceLedger;
import az.millers.hcm.leave.domain.LedgerTxType;

public interface LeaveBalanceLedgerRepository extends JpaRepository<LeaveBalanceLedger, UUID> {

    List<LeaveBalanceLedger> findByEmployeeIdAndLeaveTypeIdAndYearOrderByCreatedAtAsc(
            UUID employeeId, UUID leaveTypeId, int year);

    List<LeaveBalanceLedger> findByEmployeeIdAndYearOrderByCreatedAtDesc(UUID employeeId, int year);

    List<LeaveBalanceLedger> findByEmployeeIdAndLeaveTypeIdAndYearAndTxTypeOrderByCreatedAtDesc(
            UUID employeeId, UUID leaveTypeId, int year, LedgerTxType txType);
}
