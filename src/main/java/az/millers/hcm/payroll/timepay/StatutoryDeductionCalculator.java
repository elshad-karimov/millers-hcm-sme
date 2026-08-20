package az.millers.hcm.payroll.timepay;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

/**
 * Employee statutory deductions, as the company workbooks compute them.
 *
 * <p>Extracted from {@link TimesheetPayCalculator} so the profile engine prices
 * the same deductions from the same code rather than a second copy of it. The
 * behaviour is unchanged and is pinned by {@code TimesheetPayCalculatorTest}
 * against the January 2026 workbook.
 *
 * <h2>Two asymmetries are reproduced, not tidied</h2>
 * SPF subtracts life insurance; unemployment and compulsory insurance do not.
 * Income tax picks its bracket on {@code gross − lifeIns} but removes the
 * exempt meal portion only from the taxed amount. Both are the workbook's own
 * shape — tidying a statutory base silently changes what people are paid.
 *
 * <h2>These rates are not verified against legislation</h2>
 * 14% / 25% with a 200 AZN exemption is the oil-and-gas regime the company
 * currently applies in its spreadsheets. {@code StatutoryCalculator} elsewhere
 * in this system implements 3% / 10% / 14% as 2026 private-sector (V306). Which
 * applies to this legal entity is unresolved — see BLOCKERS Q5 — and no
 * percentage here has been checked against 2026 Azerbaijani law.
 */
@Component
public class StatutoryDeductionCalculator {

    private static final BigDecimal TAX_FREE_GROSS_FLOOR = BigDecimal.valueOf(200);
    private static final BigDecimal TAX_BRACKET = BigDecimal.valueOf(2500);
    private static final BigDecimal TAX_LOW_RATE = new BigDecimal("0.14");
    private static final BigDecimal TAX_HIGH_RATE = new BigDecimal("0.25");
    private static final BigDecimal TAX_HIGH_BASE = BigDecimal.valueOf(350);
    private static final BigDecimal SPF_RATE = new BigDecimal("0.03");
    private static final BigDecimal UNEMPLOYMENT_RATE = new BigDecimal("0.005");
    private static final BigDecimal INSURANCE_THRESHOLD = BigDecimal.valueOf(8000);
    private static final BigDecimal INSURANCE_LOW_RATE = new BigDecimal("0.02");
    private static final BigDecimal INSURANCE_HIGH_RATE = new BigDecimal("0.005");

    /** Full precision; the caller rounds only the figures it presents. */
    public record Result(
            BigDecimal incomeTax,
            BigDecimal spf,
            BigDecimal unemploymentFund,
            BigDecimal compulsoryInsurance,
            BigDecimal total) {
    }

    /**
     * @param gross     the sum of payable earnings
     * @param exempt    the portion exempt from every contribution base
     *                  (meal days x 5), still paid in full
     * @param sickPay   sick-leave amount, outside the contribution bases
     * @param lifeIns   life insurance, subtracted by tax and SPF only
     */
    public Result calculate(BigDecimal gross, BigDecimal exempt,
                            BigDecimal sickPay, BigDecimal lifeIns) {
        BigDecimal g = nz(gross);
        BigDecimal ex = nz(exempt);
        BigDecimal sick = nz(sickPay);
        BigDecimal life = nz(lifeIns);

        BigDecimal incomeTax = incomeTax(g, life, ex);
        BigDecimal spf = g.subtract(sick).subtract(life).subtract(ex).multiply(SPF_RATE);
        BigDecimal unemployment = g.subtract(ex).subtract(sick).multiply(UNEMPLOYMENT_RATE);
        BigDecimal insurance = compulsoryInsurance(g, ex, sick);

        return new Result(incomeTax, spf, unemployment, insurance,
                incomeTax.add(spf).add(unemployment).add(insurance));
    }

    private BigDecimal incomeTax(BigDecimal gross, BigDecimal lifeIns, BigDecimal exempt) {
        if (gross.compareTo(TAX_FREE_GROSS_FLOOR) < 0) return BigDecimal.ZERO;
        BigDecimal afterLifeIns = gross.subtract(lifeIns);
        if (afterLifeIns.compareTo(TAX_BRACKET) <= 0) {
            return afterLifeIns.subtract(exempt).subtract(TAX_FREE_GROSS_FLOOR).multiply(TAX_LOW_RATE);
        }
        return afterLifeIns.subtract(exempt).subtract(TAX_BRACKET)
                .multiply(TAX_HIGH_RATE).add(TAX_HIGH_BASE);
    }

    private BigDecimal compulsoryInsurance(BigDecimal gross, BigDecimal exempt, BigDecimal sick) {
        BigDecimal chargeable = gross.subtract(exempt).subtract(sick);
        if (gross.compareTo(INSURANCE_THRESHOLD) <= 0) {
            return chargeable.multiply(INSURANCE_LOW_RATE);
        }
        return chargeable.subtract(INSURANCE_THRESHOLD).multiply(INSURANCE_HIGH_RATE)
                .add(INSURANCE_THRESHOLD.multiply(INSURANCE_LOW_RATE));
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
