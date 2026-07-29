package az.millers.hcm.payroll.service;
import az.millers.hcm.common.tenant.TenantContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.payroll.domain.EmployeeCompensation;
import az.millers.hcm.payroll.domain.LoanRequest;
import az.millers.hcm.payroll.domain.LoanRequestStatus;
import az.millers.hcm.payroll.domain.LoanType;
import az.millers.hcm.payroll.domain.PayrollLoan;
import az.millers.hcm.payroll.domain.PayrollLoanStatus;
import az.millers.hcm.payroll.repo.EmployeeCompensationRepository;
import az.millers.hcm.payroll.repo.LoanRequestRepository;
import az.millers.hcm.payroll.repo.LoanTypeRepository;
import az.millers.hcm.payroll.repo.PayrollLoanRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.common.BusinessNumbers;

/**
 * M461 — Loan request service: ESS submit → eligibility check → workflow → PayrollLoan bridge.
 */
@Service
public class LoanRequestService {
    private static final String MODULE = "payroll";
    private static final String ENTITY = "LoanRequest";

    private final LoanRequestRepository repository;
    private final LoanTypeRepository loanTypeRepo;
    private final EmployeeRepository employeeRepo;
    private final EmployeeCompensationRepository compensationRepo;
    private final PayrollLoanRepository payrollLoanRepo;
    private final PayrollLoanService payrollLoanService;
    private final AuditService audit;
    private final CurrentRequest currentRequest;
    private final NamedParameterJdbcTemplate jdbc;

    public LoanRequestService(LoanRequestRepository repository, LoanTypeRepository loanTypeRepo,
                              EmployeeRepository employeeRepo, EmployeeCompensationRepository compensationRepo,
                              PayrollLoanRepository payrollLoanRepo, PayrollLoanService payrollLoanService,
                              AuditService audit, CurrentRequest currentRequest, NamedParameterJdbcTemplate jdbc) {
        this.repository = repository;
        this.loanTypeRepo = loanTypeRepo;
        this.employeeRepo = employeeRepo;
        this.compensationRepo = compensationRepo;
        this.payrollLoanRepo = payrollLoanRepo;
        this.payrollLoanService = payrollLoanService;
        this.audit = audit;
        this.currentRequest = currentRequest;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<LoanRequest> listAll() {
        return repository.findByTenantIdOrderByRequestedAtDesc(TenantContext.current());
    }

    @Transactional(readOnly = true)
    public List<LoanRequest> listByEmployee(UUID employeeId) {
        return repository.findByTenantIdAndEmployeeIdOrderByRequestedAtDesc(TenantContext.current(), employeeId);
    }

    @Transactional(readOnly = true)
    public LoanRequest get(UUID id) {
        return repository.findById(id)
                .filter(r -> TenantContext.current().equals(r.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Loan request not found"));
    }

    @Transactional
    public LoanRequest submit(UUID employeeId, UUID loanTypeId, BigDecimal requestedAmount, Integer requestedMonths, String purpose) {
        LoanType type = loanTypeRepo.findById(loanTypeId)
                .orElseThrow(() -> new BadRequestException("Loan type not found"));

        // Eligibility check
        EligibilityResult eligibility = checkEligibility(employeeId, type, requestedAmount, requestedMonths);
        if (!eligibility.passed) {
            throw new BadRequestException("ELIGIBILITY_CHECK_FAILED: " + eligibility.notes);
        }

        String requestNo = BusinessNumbers.format("LR", 5, jdbc.queryForObject("SELECT nextval('payroll.loan_request_no_seq')", Map.of(), Long.class));

        LoanRequest request = new LoanRequest();
        request.setTenantId(TenantContext.current());
        request.setRequestNo(requestNo);
        request.setEmployeeId(employeeId);
        request.setLoanTypeId(loanTypeId);
        request.setRequestedAmount(requestedAmount);
        request.setRequestedMonths(requestedMonths);
        request.setPurpose(purpose);
        request.setStatus(LoanRequestStatus.DRAFT);
        request.setEligibilityCheckPassed(true);
        request.setEligibilityNotes(eligibility.notes);
        request.setRequestedBy(currentRequest.username());
        request.setCreatedBy(currentRequest.username());
        request.setUpdatedBy(currentRequest.username());
        LoanRequest saved = repository.save(request);
        saved.setStatus(LoanRequestStatus.SUBMITTED);
        repository.save(saved);
        audit.record(MODULE, ENTITY, saved.getId().toString(), "SUBMIT", null, saved);
        return saved;
    }

    private EligibilityResult checkEligibility(UUID employeeId, LoanType type, BigDecimal requestedAmount, Integer requestedMonths) {
        StringBuilder notes = new StringBuilder();

        // 1. Employee exists and active
        Employee emp = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new BadRequestException("Employee not found"));
        if (emp.getEmploymentStatus() == null || !emp.getEmploymentStatus().toString().equals("ACTIVE")) {
            return new EligibilityResult(false, "Employee is not active");
        }

        // 2. Tenure check
        if (emp.getHireDate() != null && type.getMinTenureMonths() > 0) {
            Period tenurePeriod = Period.between(emp.getHireDate(), LocalDate.now());
            int tenureMonths = tenurePeriod.getYears() * 12 + tenurePeriod.getMonths();
            if (tenureMonths < type.getMinTenureMonths()) {
                return new EligibilityResult(false, "Minimum tenure not met: required=" + type.getMinTenureMonths() + " months, actual=" + tenureMonths);
            }
            notes.append("Tenure: ").append(tenureMonths).append(" months. ");
        }

        // 3. Amount check (max_amount OR max_multiple_of_net)
        EmployeeCompensation comp = compensationRepo.findActiveOn(employeeId, LocalDate.now()).orElse(null);
        if (comp == null) {
            return new EligibilityResult(false, "No active compensation found");
        }

        BigDecimal maxAllowed = BigDecimal.ZERO;
        if (type.getMaxAmount() != null) {
            maxAllowed = type.getMaxAmount();
        } else {
            // Use max_multiple_of_net × monthly base salary
            maxAllowed = comp.getMonthlyBaseSalary().multiply(type.getMaxMultipleOfNet()).setScale(2, RoundingMode.HALF_UP);
        }

        if (requestedAmount.compareTo(maxAllowed) > 0) {
            return new EligibilityResult(false, "Requested amount exceeds limit: max=" + maxAllowed + ", requested=" + requestedAmount);
        }
        notes.append("Amount OK (max=").append(maxAllowed).append("). ");

        // 4. Month check
        if (requestedMonths > type.getMaxMonths()) {
            return new EligibilityResult(false, "Requested months exceed limit: max=" + type.getMaxMonths() + ", requested=" + requestedMonths);
        }
        notes.append("Months OK. ");

        // 5. Active loan count check
        long activeLoanCount = payrollLoanRepo.findByEmployeeIdAndStatus(employeeId, PayrollLoanStatus.ACTIVE).size();
        if (activeLoanCount >= type.getMaxActiveLoans()) {
            return new EligibilityResult(false, "Maximum active loans reached: " + type.getMaxActiveLoans());
        }
        notes.append("Active loans: ").append(activeLoanCount).append("/").append(type.getMaxActiveLoans()).append(". ");

        return new EligibilityResult(true, notes.toString());
    }

    @Transactional
    public LoanRequest approve(UUID id) {
        LoanRequest request = get(id);
        if (request.getStatus() != LoanRequestStatus.SUBMITTED) {
            throw new BadRequestException("Only SUBMITTED requests can be approved");
        }

        // Block self-approval
        String currentUsername = currentRequest.username();
        if (currentUsername != null && currentUsername.equals(request.getRequestedBy())) {
            throw new BadRequestException("Cannot approve your own loan request");
        }

        // Belt-and-braces: also check if the requesting EMPLOYEE is the current user
        try {
            Employee currentEmployee = employeeRepo.findByUsername(currentUsername).orElse(null);
            if (currentEmployee != null && currentEmployee.getId().equals(request.getEmployeeId())) {
                throw new BadRequestException("Cannot approve your own loan request");
            }
        } catch (Exception e) {
            // Non-fatal if employee mapping doesn't exist; main guard is requestedBy check
        }

        // Bridge to PayrollLoanService.create (non-fatal)
        try {
            BigDecimal monthlyInstallment = request.getRequestedAmount()
                    .divide(new BigDecimal(request.getRequestedMonths()), 2, RoundingMode.HALF_UP);
            int currentYear = LocalDate.now().getYear();
            int currentMonth = LocalDate.now().getMonthValue();

            PayrollLoan payrollLoan = payrollLoanService.create(
                    request.getEmployeeId(),
                    request.getRequestedAmount(),
                    monthlyInstallment,
                    currentYear, currentMonth,
                    "Loan request: " + request.getRequestNo(),
                    currentRequest.username()
            );

            request.setPayrollLoanId(payrollLoan.getId());
            audit.record(MODULE, ENTITY, request.getId().toString(), "PAYROLL_LOAN_CREATED",
                    null, Map.of("payrollLoanId", payrollLoan.getId(), "monthlyInstallment", monthlyInstallment));
        } catch (Exception e) {
            audit.record(MODULE, ENTITY, id.toString(), "PAYROLL_LOAN_CREATE_FAILED",
                    null, Map.of("error", e.getMessage()));
        }

        request.setStatus(LoanRequestStatus.APPROVED);
        request.setApprovedBy(currentRequest.username());
        request.setApprovedAt(OffsetDateTime.now());
        request.setUpdatedBy(currentRequest.username());
        LoanRequest saved = repository.save(request);
        audit.record(MODULE, ENTITY, id.toString(), "APPROVE", null, Map.of("approvedBy", currentRequest.username()));
        return saved;
    }

    @Transactional
    public LoanRequest reject(UUID id, String reason) {
        LoanRequest request = get(id);
        if (request.getStatus() != LoanRequestStatus.SUBMITTED) {
            throw new BadRequestException("Only SUBMITTED requests can be rejected");
        }
        request.setStatus(LoanRequestStatus.REJECTED);
        request.setRejectedReason(reason);
        request.setUpdatedBy(currentRequest.username());
        LoanRequest saved = repository.save(request);
        audit.record(MODULE, ENTITY, id.toString(), "REJECT", null, Map.of("reason", reason));
        return saved;
    }

    @Transactional
    public LoanRequest cancel(UUID id) {
        LoanRequest request = get(id);
        if (request.getStatus() == LoanRequestStatus.APPROVED) {
            throw new BadRequestException("Cannot cancel approved loan request");
        }
        request.setStatus(LoanRequestStatus.CANCELLED);
        request.setUpdatedBy(currentRequest.username());
        LoanRequest saved = repository.save(request);
        audit.record(MODULE, ENTITY, id.toString(), "CANCEL", null, null);
        return saved;
    }

    private static class EligibilityResult {
        boolean passed;
        String notes;

        EligibilityResult(boolean passed, String notes) {
            this.passed = passed;
            this.notes = notes;
        }
    }
}
