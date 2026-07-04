package az.millers.hcm.corehr.domain;

/**
 * M128 — accounting method used to depreciate an
 * {@link EmployeeAsset}.
 *
 * <pre>
 *   NONE              — asset doesn't depreciate (e.g. land, art).
 *                       Schedule endpoint returns an empty list.
 *   STRAIGHT_LINE     — flat monthly hit:
 *                         (cost − salvage) / usefulLifeMonths
 *                       Book value floors at salvage.
 *   DECLINING_BALANCE — annual percentage applied to the current
 *                       book value, distributed across 12 months.
 *                       Rate defaults to 2 / usefulLifeYears
 *                       ("double declining") when no explicit rate.
 *                       Book value floors at salvage.
 * </pre>
 */
public enum DepreciationMethod {
    STRAIGHT_LINE,
    DECLINING_BALANCE,
    NONE
}
