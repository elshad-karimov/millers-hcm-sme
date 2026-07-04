package az.millers.hcm.businesstrip.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import az.millers.hcm.businesstrip.domain.ExpenseCategory;
import az.millers.hcm.businesstrip.domain.ExpenseItem;

/**
 * Pins the expense-claim total helper (M104).
 *
 * <p>The total is the basis for the reimbursement amount — a rounding
 * or sign regression here would produce incorrect payments.
 */
class ExpenseClaimServiceTest {

    private static ExpenseItem item(String amount) {
        ExpenseItem i = new ExpenseItem();
        i.setCategory(ExpenseCategory.OTHER);
        i.setAmount(new BigDecimal(amount));
        return i;
    }

    @Test
    void emptyListIsZero() {
        assertThat(ExpenseClaimService.total(List.of()))
                .isEqualByComparingTo("0");
    }

    @Test
    void singleItemIsItsAmount() {
        assertThat(ExpenseClaimService.total(List.of(item("150.00"))))
                .isEqualByComparingTo("150.00");
    }

    @Test
    void multipleItemsSum() {
        assertThat(ExpenseClaimService.total(
                List.of(item("100.00"), item("50.50"), item("9.99"))))
                .isEqualByComparingTo("160.49");
    }

    @Test
    void zeroItemsDoNotChangeTotal() {
        assertThat(ExpenseClaimService.total(
                List.of(item("100.00"), item("0"), item("50.00"))))
                .isEqualByComparingTo("150.00");
    }

    /** V71 CHECK: amount >= 0 — documented behaviour for zero. */
    @Test
    void zeroAmountItemsAreAllowed() {
        assertThat(ExpenseClaimService.total(List.of(item("0.00"))))
                .isEqualByComparingTo("0.00");
    }
}
