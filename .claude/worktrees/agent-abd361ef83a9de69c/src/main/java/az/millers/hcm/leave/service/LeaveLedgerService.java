package az.millers.hcm.leave.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.leave.domain.LeaveBalanceLedger;
import az.millers.hcm.leave.domain.LedgerTxType;
import az.millers.hcm.leave.repo.LeaveBalanceLedgerRepository;

@Service
public class LeaveLedgerService {

    private final LeaveBalanceLedgerRepository repo;

    public LeaveLedgerService(LeaveBalanceLedgerRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public LeaveBalanceLedger record(UUID employeeId, UUID leaveTypeId, int year,
                                     LedgerTxType txType, BigDecimal amount,
                                     LocalDate effectiveDate, String sourceType, String sourceId,
                                     BigDecimal balanceAfter, String notes, String createdBy) {
        LeaveBalanceLedger entry = new LeaveBalanceLedger();
        entry.setEmployeeId(employeeId);
        entry.setLeaveTypeId(leaveTypeId);
        entry.setYear(year);
        entry.setTxType(txType);
        entry.setAmount(amount);
        entry.setEffectiveDate(effectiveDate);
        entry.setSourceType(sourceType);
        entry.setSourceId(sourceId);
        entry.setBalanceAfter(balanceAfter);
        entry.setNotes(notes);
        entry.setCreatedBy(createdBy);
        return repo.save(entry);
    }

    @Transactional(readOnly = true)
    public List<LeaveBalanceLedger> listForEmployee(UUID employeeId, UUID leaveTypeId, int year) {
        if (leaveTypeId != null) {
            return repo.findByEmployeeIdAndLeaveTypeIdAndYearOrderByCreatedAtAsc(employeeId, leaveTypeId, year);
        }
        return repo.findByEmployeeIdAndYearOrderByCreatedAtDesc(employeeId, year);
    }
}
