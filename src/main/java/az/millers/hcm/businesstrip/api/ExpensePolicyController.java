package az.millers.hcm.businesstrip.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.businesstrip.domain.ExpenseCategory;
import az.millers.hcm.businesstrip.domain.ExpensePolicy;
import az.millers.hcm.businesstrip.service.ExpensePolicyService;

/**
 * Expense policy rules (M454).
 */
@RestController
@RequestMapping("/api/business-trips/expense-policies")
public class ExpensePolicyController {

    private final ExpensePolicyService service;

    public ExpensePolicyController(ExpensePolicyService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('READ_HR')")
    public List<ExpensePolicyResponse> list() {
        return service.listActive().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('READ_HR')")
    public ExpensePolicyResponse get(@PathVariable UUID id) {
        return toResponse(service.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('WRITE_HR_ADMIN')")
    public ExpensePolicyResponse create(@RequestBody ExpensePolicyRequest req) {
        ExpensePolicy policy = new ExpensePolicy();
        applyRequest(policy, req);
        return toResponse(service.create(policy));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('WRITE_HR_ADMIN')")
    public ExpensePolicyResponse update(@PathVariable UUID id, @RequestBody ExpensePolicyRequest req) {
        ExpensePolicy updated = new ExpensePolicy();
        applyRequest(updated, req);
        return toResponse(service.update(id, updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('WRITE_HR_ADMIN')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    private void applyRequest(ExpensePolicy policy, ExpensePolicyRequest req) {
        policy.setCategory(req.category());
        policy.setEmployeeGrade(req.employeeGrade());
        policy.setMaxPerTransaction(req.maxPerTransaction());
        policy.setMaxDaily(req.maxDaily());
        policy.setReceiptRequiredAbove(req.receiptRequiredAbove() != null
                ? req.receiptRequiredAbove() : new BigDecimal("20.00"));
        policy.setBlocked(req.blocked() != null ? req.blocked() : false);
        policy.setEffectiveFrom(req.effectiveFrom());
        policy.setEffectiveTo(req.effectiveTo());
        policy.setActive(req.active() != null ? req.active() : true);
    }

    private ExpensePolicyResponse toResponse(ExpensePolicy p) {
        return new ExpensePolicyResponse(
                p.getId(), p.getCategory(), p.getEmployeeGrade(),
                p.getMaxPerTransaction(), p.getMaxDaily(), p.getReceiptRequiredAbove(),
                p.isBlocked(), p.getEffectiveFrom(), p.getEffectiveTo(),
                p.isActive(), p.getCreatedAt(), p.getUpdatedAt());
    }

    public record ExpensePolicyRequest(
            ExpenseCategory category,
            String employeeGrade,
            BigDecimal maxPerTransaction,
            BigDecimal maxDaily,
            BigDecimal receiptRequiredAbove,
            Boolean blocked,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            Boolean active) {
    }

    public record ExpensePolicyResponse(
            UUID id,
            ExpenseCategory category,
            String employeeGrade,
            BigDecimal maxPerTransaction,
            BigDecimal maxDaily,
            BigDecimal receiptRequiredAbove,
            boolean blocked,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            boolean active,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {
    }
}
