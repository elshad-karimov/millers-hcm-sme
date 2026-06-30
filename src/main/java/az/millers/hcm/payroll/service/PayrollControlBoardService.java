package az.millers.hcm.payroll.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.payroll.api.dto.PayrollControlBoardResponse;
import az.millers.hcm.payroll.api.dto.PayrollControlBoardResponse.CurrentRunSummary;
import az.millers.hcm.payroll.domain.PayrollLoan;
import az.millers.hcm.payroll.domain.PayrollLoanStatus;
import az.millers.hcm.payroll.domain.PayrollResult;
import az.millers.hcm.payroll.domain.PayrollRun;
import az.millers.hcm.payroll.domain.PayrollRunStatus;
import az.millers.hcm.payroll.domain.RunType;
import az.millers.hcm.payroll.domain.SalaryAdvanceStatus;
import az.millers.hcm.payroll.repo.PayrollLoanRepository;
import az.millers.hcm.payroll.repo.PayrollResultRepository;
import az.millers.hcm.payroll.repo.PayrollRunRepository;
import az.millers.hcm.payroll.repo.SalaryAdvanceRepository;

@Service
public class PayrollControlBoardService {

    private final PayrollRunRepository runRepo;
    private final PayrollResultRepository resultRepo;
    private final PayrollLoanRepository loanRepo;
    private final SalaryAdvanceRepository advanceRepo;

    public PayrollControlBoardService(PayrollRunRepository runRepo,
                                      PayrollResultRepository resultRepo,
                                      PayrollLoanRepository loanRepo,
                                      SalaryAdvanceRepository advanceRepo) {
        this.runRepo = runRepo;
        this.resultRepo = resultRepo;
        this.loanRepo = loanRepo;
        this.advanceRepo = advanceRepo;
    }

    @Transactional(readOnly = true)
    public PayrollControlBoardResponse dashboard() {
        // Find current open run: latest non-PAID/non-CLOSED REGULAR run
        List<PayrollRun> allRuns = runRepo.findAllByOrderByPeriodYearDescPeriodMonthDesc();
        PayrollRun currentRun = allRuns.stream()
                .filter(r -> r.getRunType() == RunType.REGULAR)
                .filter(r -> r.getStatus() != PayrollRunStatus.PAID && r.getStatus() != PayrollRunStatus.CLOSED)
                .findFirst()
                .orElse(null);

        // If no open run, fall back to the latest run (regardless of status)
        if (currentRun == null && !allRuns.isEmpty()) {
            currentRun = allRuns.get(0);
        }

        if (currentRun == null) {
            // No runs at all
            return new PayrollControlBoardResponse(
                    null, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    null, BigDecimal.ZERO, 0);
        }

        List<PayrollResult> results = resultRepo.findByRunIdOrderByEmployeeIdAsc(currentRun.getId());
        int headcount = results.size();

        BigDecimal totalGross = results.stream()
                .map(PayrollResult::getGrossAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalNet = results.stream()
                .map(PayrollResult::getNetAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalTax = results.stream()
                .map(PayrollResult::getIncomeTax)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Month-over-month gross variance vs prior PAID REGULAR run
        BigDecimal momGrossVariancePct = null;
        PayrollRun priorRun = allRuns.stream()
                .filter(r -> r.getRunType() == RunType.REGULAR)
                .filter(r -> r.getStatus() == PayrollRunStatus.PAID)
                .findFirst()
                .orElse(null);

        if (priorRun != null) {
            List<PayrollResult> priorResults = resultRepo.findByRunIdOrderByEmployeeIdAsc(priorRun.getId());
            BigDecimal priorGross = priorResults.stream()
                    .map(PayrollResult::getGrossAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (priorGross.compareTo(BigDecimal.ZERO) > 0) {
                momGrossVariancePct = totalGross.subtract(priorGross)
                        .divide(priorGross, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
            }
        }

        // Outstanding loan balance
        List<PayrollLoan> activeLoans = loanRepo.findByStatus(PayrollLoanStatus.ACTIVE);
        BigDecimal outstandingLoanBalance = activeLoans.stream()
                .map(PayrollLoan::getOutstandingBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Pending advances count
        long pendingAdvanceCount = advanceRepo.countByStatus(SalaryAdvanceStatus.PENDING);

        CurrentRunSummary runSummary = new CurrentRunSummary(
                currentRun.getId(),
                currentRun.getPeriodYear(),
                currentRun.getPeriodMonth(),
                currentRun.getStatus(),
                currentRun.getRunType()
        );

        return new PayrollControlBoardResponse(
                runSummary,
                headcount,
                totalGross,
                totalNet,
                totalTax,
                momGrossVariancePct,
                outstandingLoanBalance,
                (int) pendingAdvanceCount
        );
    }
}
