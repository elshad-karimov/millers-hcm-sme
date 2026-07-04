package az.millers.hcm.leave.api.dto;

import java.math.BigDecimal;

/**
 * Immutable value object representing one tier in a seniority-based leave
 * entitlement schedule (PRD 8.5.2 — milestone 47).
 *
 * <p>Example annual-leave schedule for Azerbaijan Labour Code defaults:
 * <pre>
 *   [{ yearsMin:0,  yearsMax:4,  annualDays:21 },
 *    { yearsMin:5,  yearsMax:9,  annualDays:24 },
 *    { yearsMin:10, yearsMax:14, annualDays:27 },
 *    { yearsMin:15,              annualDays:30 }]
 * </pre>
 *
 * <p>Brackets must not overlap.  The accrual walker finds the first
 * bracket where {@code yearsOfService >= yearsMin && (yearsMax == null
 * || yearsOfService <= yearsMax)} and divides {@code annualDays / 12}
 * to get the monthly bump.
 *
 * @param yearsMin   inclusive lower bound of completed years of service (≥ 0)
 * @param yearsMax   inclusive upper bound — {@code null} means "and above"
 * @param annualDays annual entitlement days when this bracket applies (> 0)
 */
public record SeniorityBracket(int yearsMin, Integer yearsMax, BigDecimal annualDays) {
}
