package az.millers.hcm.leave.domain;

/**
 * M341: Discriminator that controls how a leave type is measured and requested.
 *
 * <ul>
 *   <li>{@code DAYS}     — whole-day or half-day requests (existing behaviour).</li>
 *   <li>{@code HALF_DAY} — the leave type only accepts half-day requests; enforced
 *       at submission time so HR doesn't need to remember to tick the checkbox.</li>
 *   <li>{@code HOURS}    — requests carry a start time, end time, and
 *       {@code duration_hours}; the system converts hours → fractional days using
 *       {@code leave_type.hours_per_day} for balance deduction.</li>
 * </ul>
 */
public enum LeaveUnit {
    DAYS, HALF_DAY, HOURS
}
