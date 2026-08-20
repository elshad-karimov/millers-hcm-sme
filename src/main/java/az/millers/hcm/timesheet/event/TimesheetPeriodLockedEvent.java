package az.millers.hcm.timesheet.event;

import java.util.List;
import java.util.UUID;

/**
 * Published when an attendance period is locked and its approved months become
 * immutable.
 *
 * <p>This is the boundary the payroll design depends on: payroll never consumes
 * raw time, it consumes a locked summary. The lock is therefore the earliest
 * moment downstream modules may safely read a month's quantities.
 *
 * @param year         the period year
 * @param month        the period month
 * @param timesheetIds the months that moved to LOCKED in this operation
 * @param lockedBy     who locked it
 */
public record TimesheetPeriodLockedEvent(
        int year,
        int month,
        List<UUID> timesheetIds,
        String lockedBy) {
}
