package az.millers.hcm.staffing.domain;

/**
 * Relationship between an employee and a position seat (M246 / PRD §15, §32).
 *
 * <p>One employee can hold simultaneous rows of different types — e.g. a
 * Senior Accountant (PRIMARY) who is also ACTING as Finance Director
 * while the Director is on leave.
 */
public enum OccupancyType {
    /** Regular long-term occupant. The vast majority of rows. */
    PRIMARY,
    /** Non-primary seat held in addition to a primary role (matrix/dotted-line). */
    SECONDARY,
    /** Temporarily fulfilling a higher role while still holding their home position. */
    ACTING,
    /** Short-term cover (maternity replacement, project-based fill, etc.). */
    TEMPORARY,
    /** Sent to another org / role for a defined period. */
    SECONDMENT,
    /** Non-permanent intern occupying a seat. */
    INTERN,
    /** Non-employee contractor occupying a seat. */
    CONTRACTOR;

    /** True if this type counts toward {@code Position.occupiedHeadcount}. */
    public boolean countsAsOccupied() {
        // SECONDARY and SECONDMENT are not against the seat's primary
        // headcount — the home position bears the head. ACTING + TEMPORARY
        // count because no one else is permanently in the seat.
        return this == PRIMARY || this == ACTING || this == TEMPORARY
                || this == INTERN || this == CONTRACTOR;
    }
}
