package az.millers.hcm.compensation.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.compensation.domain.CommissionPayout;
import az.millers.hcm.compensation.domain.IncentivePayout;
import az.millers.hcm.compensation.domain.PayrollCompTransfer;
import az.millers.hcm.compensation.repo.CommissionPayoutRepository;
import az.millers.hcm.compensation.repo.IncentivePayoutRepository;
import az.millers.hcm.compensation.repo.PayrollCompTransferRepository;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.domain.EmploymentStatus;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.payroll.api.dto.AddBonusRequest;
import az.millers.hcm.payroll.domain.BonusType;
import az.millers.hcm.payroll.domain.PayrollBonus;
import az.millers.hcm.payroll.domain.PayrollRun;
import az.millers.hcm.payroll.domain.PayrollRunStatus;
import az.millers.hcm.payroll.service.PayrollRunService;
import az.millers.hcm.security.CurrentRequest;

/**
 * M369 — Compensation → Payroll Transfer service.
 * Transfers APPROVED incentive and commission payouts to a target payroll run
 * as one-time bonuses. Idempotent, skips terminated employees.
 */
@Service
public class PayrollCompTransferService {

    private static final Logger log = LoggerFactory.getLogger(PayrollCompTransferService.class);
    private static final String MODULE = "compensation";
    private static final String TENANT = "default";

    private final PayrollCompTransferRepository transfers;
    private final IncentivePayoutRepository incentivePayouts;
    private final CommissionPayoutRepository commissionPayouts;
    private final PayrollRunService payrollRunService;
    private final EmployeeRepository employees;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public PayrollCompTransferService(PayrollCompTransferRepository transfers,
                                       IncentivePayoutRepository incentivePayouts,
                                       CommissionPayoutRepository commissionPayouts,
                                       PayrollRunService payrollRunService,
                                       EmployeeRepository employees,
                                       AuditService audit,
                                       CurrentRequest currentRequest) {
        this.transfers = transfers;
        this.incentivePayouts = incentivePayouts;
        this.commissionPayouts = commissionPayouts;
        this.payrollRunService = payrollRunService;
        this.employees = employees;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    /**
     * Transfer all APPROVED incentive and commission payouts that haven't been transferred
     * to the specified payroll run. The run must be DRAFT, CALCULATED, REOPENED, or APPROVED
     * (not PAID or CLOSED).
     */
    @Transactional
    public Map<String, Object> transferApprovedToRun(UUID targetPayrollRunId) {
        PayrollRun run = payrollRunService.get(targetPayrollRunId);

        // Validate run status
        if (run.getStatus() == PayrollRunStatus.PAID || run.getStatus() == PayrollRunStatus.CLOSED) {
            throw new BadRequestException("Cannot transfer to a " + run.getStatus() + " run");
        }

        int transferred = 0;
        int skipped = 0;

        // Transfer incentive payouts
        List<IncentivePayout> incentives = incentivePayouts.findByTenantIdAndStatusOrderByCreatedAtDesc(TENANT, "APPROVED");
        for (IncentivePayout payout : incentives) {
            // Skip if already transferred
            if (payout.getPayrollBonusId() != null) {
                skipped++;
                continue;
            }

            // Check for existing transfer record (idempotent)
            if (transfers.findBySourceTypeAndSourceId("INCENTIVE", payout.getId()).isPresent()) {
                skipped++;
                continue;
            }

            // Check if employee is terminated
            Employee emp = employees.findById(payout.getEmployeeId()).orElse(null);
            if (emp == null || emp.getEmploymentStatus() == EmploymentStatus.TERMINATED) {
                log.info("Skipping incentive payout {} for terminated employee {}", payout.getId(), payout.getEmployeeId());
                skipped++;
                continue;
            }

            try {
                // Create payroll bonus
                AddBonusRequest bonusReq = new AddBonusRequest(
                    payout.getEmployeeId(),
                    BonusType.ONE_TIME,
                    payout.getPayoutAmount(),
                    "Incentive payout: " + payout.getPeriod()
                );
                PayrollBonus bonus = payrollRunService.addBonus(targetPayrollRunId, bonusReq);

                // Update payout
                payout.setPayrollBonusId(bonus.getId());
                payout.setStatus("PAID");
                incentivePayouts.save(payout);

                // Record transfer
                PayrollCompTransfer transfer = new PayrollCompTransfer();
                transfer.setTenantId(TENANT);
                transfer.setSourceType("INCENTIVE");
                transfer.setSourceId(payout.getId());
                transfer.setEmployeeId(payout.getEmployeeId());
                transfer.setTargetRunId(targetPayrollRunId);
                transfer.setAmount(payout.getPayoutAmount());
                transfer.setPayrollBonusId(bonus.getId());
                transfer.setStatus("TRANSFERRED");
                transfer.setTransferredBy(currentRequest.username());
                transfers.save(transfer);

                transferred++;
            } catch (Exception e) {
                log.error("Failed to transfer incentive payout {}: {}", payout.getId(), e.getMessage(), e);
                skipped++;
            }
        }

        // Transfer commission payouts
        List<CommissionPayout> commissions = commissionPayouts.findByTenantIdAndStatusOrderByCreatedAtDesc(TENANT, "APPROVED");
        for (CommissionPayout payout : commissions) {
            // Skip if already transferred
            if (payout.getPayrollBonusId() != null) {
                skipped++;
                continue;
            }

            // Check for existing transfer record (idempotent)
            if (transfers.findBySourceTypeAndSourceId("COMMISSION", payout.getId()).isPresent()) {
                skipped++;
                continue;
            }

            // Check if employee is terminated
            Employee emp = employees.findById(payout.getEmployeeId()).orElse(null);
            if (emp == null || emp.getEmploymentStatus() == EmploymentStatus.TERMINATED) {
                log.info("Skipping commission payout {} for terminated employee {}", payout.getId(), payout.getEmployeeId());
                skipped++;
                continue;
            }

            try {
                // Create payroll bonus
                AddBonusRequest bonusReq = new AddBonusRequest(
                    payout.getEmployeeId(),
                    BonusType.ONE_TIME,
                    payout.getCommissionAmount(),
                    "Commission payout: " + payout.getPeriod()
                );
                PayrollBonus bonus = payrollRunService.addBonus(targetPayrollRunId, bonusReq);

                // Update payout
                payout.setPayrollBonusId(bonus.getId());
                payout.setStatus("PAID");
                commissionPayouts.save(payout);

                // Record transfer
                PayrollCompTransfer transfer = new PayrollCompTransfer();
                transfer.setTenantId(TENANT);
                transfer.setSourceType("COMMISSION");
                transfer.setSourceId(payout.getId());
                transfer.setEmployeeId(payout.getEmployeeId());
                transfer.setTargetRunId(targetPayrollRunId);
                transfer.setAmount(payout.getCommissionAmount());
                transfer.setPayrollBonusId(bonus.getId());
                transfer.setStatus("TRANSFERRED");
                transfer.setTransferredBy(currentRequest.username());
                transfers.save(transfer);

                transferred++;
            } catch (Exception e) {
                log.error("Failed to transfer commission payout {}: {}", payout.getId(), e.getMessage(), e);
                skipped++;
            }
        }

        audit.record(MODULE, "PayrollCompTransfer", targetPayrollRunId.toString(),
                "TRANSFER_TO_RUN", null,
                Map.of("transferred", transferred, "skipped", skipped,
                        "runNo", run.getRunNo()));

        return Map.of("transferredCount", transferred, "skippedCount", skipped);
    }

    @Transactional(readOnly = true)
    public List<PayrollCompTransfer> list(String status) {
        if (status != null) {
            return transfers.findByTenantIdAndStatusOrderByTransferredAtDesc(TENANT, status);
        }
        return transfers.findByTenantIdOrderByTransferredAtDesc(TENANT);
    }
}
