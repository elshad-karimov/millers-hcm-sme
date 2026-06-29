package az.millers.hcm.leave.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.leave.api.dto.LeaveBalanceAdjustment;
import az.millers.hcm.leave.domain.LeaveEncashment;
import az.millers.hcm.leave.domain.LeaveType;
import az.millers.hcm.leave.domain.LedgerTxType;
import az.millers.hcm.leave.repo.LeaveBalanceRepository;
import az.millers.hcm.leave.repo.LeaveEncashmentRepository;
import az.millers.hcm.leave.repo.LeaveTypeRepository;
import az.millers.hcm.payroll.api.dto.AddBonusRequest;
import az.millers.hcm.payroll.domain.BonusType;
import az.millers.hcm.payroll.domain.PayrollRun;
import az.millers.hcm.payroll.repo.EmployeeCompensationRepository;
import az.millers.hcm.payroll.repo.PayrollRunRepository;
import az.millers.hcm.payroll.service.PayrollRunService;
import az.millers.hcm.security.CurrentRequest;

/**
 * M344 — Leave Encashment: converts remaining leave balance to a cash payment.
 *
 * <p>Rules:
 * <ul>
 *   <li>Leave type must be paid=true and encashable=true.</li>
 *   <li>Employee must have sufficient remaining balance.</li>
 *   <li>Amount = days × (monthlyBaseSalary / 22). Default working days = 22.</li>
 *   <li>Balance is debited immediately via ENCASHMENT ledger entry.</li>
 *   <li>A PayrollBonus ONE_TIME row is created in the DRAFT payroll run for the
 *       given year/month, making it appear on the next payslip.</li>
 *   <li>Reversal: cancels the bonus and restores the balance via PAYROLL_CORRECTION.</li>
 * </ul>
 */
@Service
public class LeaveEncashmentService {

    private static final int DEFAULT_WORKING_DAYS = 22;

    private static final String MODULE = "LEAVE";

    private final LeaveEncashmentRepository encashments;
    private final LeaveTypeRepository leaveTypes;
    private final LeaveBalanceRepository leaveBalances;
    private final LeaveBalanceService balanceService;
    private final LeaveLedgerService ledger;
    private final EmployeeCompensationRepository compensations;
    private final PayrollRunRepository payrollRuns;
    private final PayrollRunService payrollRunService;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public LeaveEncashmentService(LeaveEncashmentRepository encashments,
                                   LeaveTypeRepository leaveTypes,
                                   LeaveBalanceRepository leaveBalances,
                                   LeaveBalanceService balanceService,
                                   LeaveLedgerService ledger,
                                   EmployeeCompensationRepository compensations,
                                   PayrollRunRepository payrollRuns,
                                   PayrollRunService payrollRunService,
                                   AuditService audit,
                                   CurrentRequest currentRequest) {
        this.encashments = encashments;
        this.leaveTypes = leaveTypes;
        this.leaveBalances = leaveBalances;
        this.balanceService = balanceService;
        this.ledger = ledger;
        this.compensations = compensations;
        this.payrollRuns = payrollRuns;
        this.payrollRunService = payrollRunService;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<LeaveEncashment> listForEmployee(UUID employeeId) {
        return encashments.findByEmployeeIdOrderByCreatedAtDesc(employeeId);
    }

    /**
     * Encashes {@code days} of leave for an employee.
     *
     * @param employeeId   employee
     * @param leaveTypeId  must be paid=true and encashable=true
     * @param year         leave balance year to debit
     * @param days         number of days to encash (> 0)
     * @param payrollYear  payroll period to credit the bonus in
     * @param payrollMonth payroll period month
     * @param notes        optional notes
     */
    @Transactional
    public LeaveEncashment encash(UUID employeeId, UUID leaveTypeId, int year,
                                   BigDecimal days, int payrollYear, int payrollMonth,
                                   String notes) {
        if (days == null || days.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Days to encash must be positive");
        }

        LeaveType type = leaveTypes.findById(leaveTypeId)
                .orElseThrow(() -> new BadRequestException("Leave type not found"));
        if (!type.isPaid()) throw new BadRequestException("Cannot encash unpaid leave type");
        if (!type.isEncashable()) throw new BadRequestException("Leave type '" + type.getCode() + "' is not marked as encashable");

        var balance = leaveBalances.findByEmployeeIdAndLeaveTypeIdAndYear(employeeId, leaveTypeId, year)
                .orElseThrow(() -> new BadRequestException("No balance record for this employee/type/year"));

        if (balance.remaining().compareTo(days) < 0) {
            throw new BadRequestException("Insufficient balance: remaining=" + balance.remaining() + " requested=" + days);
        }

        BigDecimal salary = compensations
                .findActiveOn(employeeId, LocalDate.now())
                .map(c -> c.getMonthlyBaseSalary())
                .orElse(BigDecimal.ZERO);

        BigDecimal dailyRate = salary.divide(BigDecimal.valueOf(DEFAULT_WORKING_DAYS), 4, RoundingMode.HALF_UP);
        BigDecimal amount = dailyRate.multiply(days).setScale(2, RoundingMode.HALF_UP);

        // Debit the leave balance via adjustment (negative delta) — not commit() which is for approvals
        balanceService.applyAdjustment(new LeaveBalanceAdjustment(
                employeeId, leaveTypeId, year, days.negate(), "Leave encashment: " + days + " day(s)"));
        BigDecimal balanceAfter = balance.remaining().subtract(days);
        ledger.record(employeeId, leaveTypeId, year,
                LedgerTxType.ENCASHMENT, days.negate(), LocalDate.now(),
                "ENCASHMENT", null, balanceAfter, "Encashment: " + days + " days @ " + dailyRate,
                currentRequest.username());

        // Find an open payroll run for the given period and create a ONE_TIME bonus
        UUID bonusId = null;
        var optRun = payrollRuns.findByPeriodYearAndPeriodMonthAndJurisdiction(payrollYear, payrollMonth, "AZ");
        if (optRun.isPresent()) {
            PayrollRun run = optRun.get();
            var bonus = payrollRunService.addBonus(run.getId(),
                    new AddBonusRequest(employeeId, BonusType.ONE_TIME, amount,
                            "Leave encashment: " + days + " day(s) of " + type.getCode()));
            bonusId = bonus.getId();
        }

        LeaveEncashment enc = new LeaveEncashment();
        enc.setEmployeeId(employeeId);
        enc.setLeaveTypeId(leaveTypeId);
        enc.setYear(year);
        enc.setDaysEncashed(days);
        enc.setDailyRate(dailyRate);
        enc.setAmount(amount);
        enc.setPayrollBonusId(bonusId);
        enc.setStatus(bonusId != null ? "PAID" : "PENDING");
        enc.setNotes(notes);
        enc.setCreatedBy(currentRequest.username());
        LeaveEncashment saved = encashments.save(enc);
        audit.record(MODULE, "LeaveEncashment", saved.getId().toString(),
                "ENCASHMENT_CREATE", null, saved);
        return saved;
    }

    @Transactional
    public LeaveEncashment reverse(UUID encashmentId) {
        LeaveEncashment enc = encashments.findById(encashmentId)
                .orElseThrow(() -> new BadRequestException("Encashment not found"));
        if ("REVERSED".equals(enc.getStatus())) {
            throw new BadRequestException("Encashment already reversed");
        }

        // Restore balance via a PAYROLL_CORRECTION ledger entry
        var balance = leaveBalances.findByEmployeeIdAndLeaveTypeIdAndYear(
                enc.getEmployeeId(), enc.getLeaveTypeId(), enc.getYear()).orElse(null);
        BigDecimal balanceAfter = balance != null ? balance.remaining().add(enc.getDaysEncashed()) : enc.getDaysEncashed();
        if (balance != null) {
            balanceService.applyAdjustment(new LeaveBalanceAdjustment(
                    enc.getEmployeeId(), enc.getLeaveTypeId(), enc.getYear(),
                    enc.getDaysEncashed(), "Encashment reversal"));
        }
        ledger.record(enc.getEmployeeId(), enc.getLeaveTypeId(), enc.getYear(),
                LedgerTxType.PAYROLL_CORRECTION, enc.getDaysEncashed(), LocalDate.now(),
                "ENCASHMENT_REVERSAL", encashmentId.toString(), balanceAfter,
                "Reversal of encashment " + encashmentId, currentRequest.username());

        enc.setStatus("REVERSED");
        LeaveEncashment reversed = encashments.save(enc);
        audit.record(MODULE, "LeaveEncashment", reversed.getId().toString(),
                "ENCASHMENT_REVERSE", null, reversed);
        return reversed;
    }
}
