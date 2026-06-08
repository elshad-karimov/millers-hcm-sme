package az.millers.hcm.payroll.api;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.common.PageResponse;
import az.millers.hcm.payroll.api.dto.PayrollDeductionRequest;
import az.millers.hcm.payroll.domain.PayrollDeduction;
import az.millers.hcm.payroll.service.PayrollDeductionService;
import jakarta.validation.Valid;

/**
 * Manages per-employee payroll deductions (M181 / PRD §8.9.1).
 *
 * <p>Deductions include one-off adjustments, recurring deductions (e.g.,
 * garnishments), and salary-advance installment recoveries.
 * They are applied automatically by the PayrollEngine during each run.
 */
@RestController
@RequestMapping("/api/payroll/deductions")
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','PAYROLL_SPECIALIST')")
public class PayrollDeductionController {

    private final PayrollDeductionService svc;

    public PayrollDeductionController(PayrollDeductionService svc) {
        this.svc = svc;
    }

    @GetMapping
    public PageResponse<PayrollDeduction> list(
            @RequestParam UUID employeeId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(
                svc.list(employeeId, status,
                        PageRequest.of(page, size, Sort.by("createdAt").descending())),
                d -> d);
    }

    @GetMapping("/{id}")
    public PayrollDeduction get(@PathVariable UUID id) {
        return svc.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PayrollDeduction create(@Valid @RequestBody PayrollDeductionRequest req) {
        return svc.create(req);
    }

    /**
     * Cancels an ACTIVE deduction. Cancelled deductions are no longer applied
     * in future payroll runs.
     */
    @DeleteMapping("/{id}")
    public PayrollDeduction cancel(@PathVariable UUID id) {
        return svc.cancel(id);
    }
}
