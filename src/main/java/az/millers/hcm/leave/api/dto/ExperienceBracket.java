package az.millers.hcm.leave.api.dto;

import java.math.BigDecimal;

/**
 * M151 — one tier of the Art. 116.1 seniority uplift, bracketed on
 * <em>total professional experience</em>.
 *
 * <p>Not to be confused with {@link SeniorityBracket}, which brackets on
 * company tenure and expresses a whole annual entitlement. This record adds
 * days on top of the base, and the customer's register settled which basis is
 * in force: tenure-based brackets reproduced 19 of 136 rows, experience-based
 * brackets 126 of 131.
 *
 * <p>The Azerbaijani schedule in that register is:
 * <pre>
 *   [{ minYears: 5,  days: 2 },
 *    { minYears: 10, days: 4 },
 *    { minYears: 15, days: 6 }]
 * </pre>
 * Below the lowest {@code minYears} the uplift is zero. The resolver picks the
 * highest bracket whose {@code minYears} the employee has reached, so the
 * tiers need no upper bounds and cannot leave a gap.
 *
 * @param minYears inclusive lower bound of completed professional years (≥ 0)
 * @param days     days added when this bracket is the highest one reached (≥ 0)
 */
public record ExperienceBracket(int minYears, BigDecimal days) {
}
