package az.millers.hcm.businesstrip.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.businesstrip.domain.ExpenseCategory;
import az.millers.hcm.businesstrip.domain.ExpensePolicy;
import az.millers.hcm.businesstrip.repo.ExpensePolicyRepository;
import az.millers.hcm.common.ResourceNotFoundException;

/**
 * Expense policy validation engine (M454).
 */
@Service
public class ExpensePolicyService {

    private final ExpensePolicyRepository policies;

    public ExpensePolicyService(ExpensePolicyRepository policies) {
        this.policies = policies;
    }

    /**
     * Validate an expense line item against policy.
     *
     * @param category expense category
     * @param amount expense amount
     * @param date expense date
     * @param grade employee grade (nullable)
     * @param hasReceipt whether receipt is attached
     * @return validation result (VALID | WARNING | RECEIPT_REQUIRED | BLOCKED)
     */
    @Transactional(readOnly = true)
    public ValidationResult validate(ExpenseCategory category, BigDecimal amount,
                                      LocalDate date, String grade, boolean hasReceipt) {
        String tenantId = "default";
        List<ExpensePolicy> matches = policies.findMatchingPolicies(
                tenantId, category, grade, date);

        if (matches.isEmpty()) {
            // No policy = allow with warning
            return new ValidationResult(ValidationVerdict.VALID,
                    "No policy defined for " + category, null);
        }

        // Most specific policy wins
        ExpensePolicy policy = matches.get(0);
        List<String> flags = new ArrayList<>();

        if (policy.isBlocked()) {
            flags.add("BLOCKED");
            return new ValidationResult(ValidationVerdict.BLOCKED,
                    category + " expenses are blocked by policy", policy.getId());
        }

        // Check receipt requirement
        if (amount.compareTo(policy.getReceiptRequiredAbove()) > 0 && !hasReceipt) {
            flags.add("RECEIPT_REQUIRED");
            return new ValidationResult(ValidationVerdict.RECEIPT_REQUIRED,
                    "Receipt required for " + category + " > " + policy.getReceiptRequiredAbove(),
                    policy.getId());
        }

        // Check limits
        if (policy.getMaxPerTransaction() != null
                && amount.compareTo(policy.getMaxPerTransaction()) > 0) {
            flags.add("WARNING");
            return new ValidationResult(ValidationVerdict.WARNING,
                    category + " exceeds per-transaction limit: " + policy.getMaxPerTransaction(),
                    policy.getId());
        }

        // Daily limit check requires aggregating same-day same-category expenses — deferred seam.
        // For now, just validate per-transaction.

        return new ValidationResult(ValidationVerdict.VALID, null, policy.getId());
    }

    // ── CRUD for expense policies (HR_ADMIN) ────────────────────────────────

    @Transactional(readOnly = true)
    public List<ExpensePolicy> listActive() {
        String tenantId = "default";
        return policies.findByTenantIdAndActiveOrderByCategory(tenantId, true);
    }

    @Transactional(readOnly = true)
    public ExpensePolicy get(UUID id) {
        String tenantId = "default";
        ExpensePolicy policy = policies.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense policy not found: " + id));
        return policy;
    }

    @Transactional
    public ExpensePolicy create(ExpensePolicy policy) {
        policy.setTenantId("default");
        return policies.save(policy);
    }

    @Transactional
    public ExpensePolicy update(UUID id, ExpensePolicy updated) {
        ExpensePolicy existing = get(id);
        existing.setCategory(updated.getCategory());
        existing.setEmployeeGrade(updated.getEmployeeGrade());
        existing.setMaxPerTransaction(updated.getMaxPerTransaction());
        existing.setMaxDaily(updated.getMaxDaily());
        existing.setReceiptRequiredAbove(updated.getReceiptRequiredAbove());
        existing.setBlocked(updated.isBlocked());
        existing.setEffectiveFrom(updated.getEffectiveFrom());
        existing.setEffectiveTo(updated.getEffectiveTo());
        existing.setActive(updated.isActive());
        return policies.save(existing);
    }

    @Transactional
    public void delete(UUID id) {
        ExpensePolicy policy = get(id);
        policy.setActive(false);
        policies.save(policy);
    }

    /**
     * Validation verdict.
     */
    public enum ValidationVerdict {
        VALID,
        WARNING,        // Over limit but allowed
        RECEIPT_REQUIRED,
        BLOCKED
    }

    /**
     * Validation result.
     */
    public record ValidationResult(
            ValidationVerdict verdict,
            String message,
            UUID matchedPolicyId) {
    }
}
