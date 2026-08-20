package az.millers.hcm.corehr.domain;

/**
 * M150 — the employee's standing work category: where they are contracted to
 * work, as recorded in the personnel register.
 *
 * <p>Deliberately <em>not</em> the same type as
 * {@link az.millers.hcm.timesheet.domain.WorkType}, which classifies a single
 * timesheet day and therefore also carries non-working states (business trip,
 * remote, leave, sick, non-working). Those are meaningless as a standing
 * category — nobody is contracted as "SICK" — so the two stay separate and
 * only the three genuinely shared site categories overlap by name.
 *
 * <ul>
 *   <li>{@link #ONSHORE} — base/city office; the base monthly rate applies.</li>
 *   <li>{@link #OFFSHORE} — offshore assignment; the offshore uplift applies
 *       and the offshore rotation governs.</li>
 *   <li>{@link #QUAYSIDE} — quayside/yard work; its own rate sits between
 *       onshore and offshore.</li>
 *   <li>{@link #HYBRID} — split across sites within a period, so the
 *       applicable rate is resolved per timesheet day rather than from this
 *       field alone.</li>
 * </ul>
 *
 * <p>Distinct from {@link EmploymentType} (the contractual form) and from the
 * work location (the specific site). The rates themselves live in the
 * payroll/compensation modules — this field never carries an amount.
 */
public enum EmployeeWorkType {
    ONSHORE,
    OFFSHORE,
    QUAYSIDE,
    HYBRID
}
