package az.millers.hcm.corehr.domain;

/**
 * Kind of {@link EmployeeAssignment} (M75 / P2-20).
 *
 * <pre>
 *   PRIMARY     — the employee's main role; at most one open per employee
 *   ACTING      — temporarily filling a vacant position (e.g. interim manager)
 *   SECONDMENT  — loaned to another business unit for a fixed period
 *   MATRIX      — dotted-line reporting to another team in addition to PRIMARY
 *   PROJECT     — explicit allocation to a project for a fixed period
 * </pre>
 *
 * <p>The partial unique index {@code uq_ea_one_open_primary} (V61) enforces
 * the "at most one open PRIMARY per employee" rule at the DB level.
 */
public enum AssignmentType {
    PRIMARY,
    ACTING,
    SECONDMENT,
    MATRIX,
    PROJECT
}
