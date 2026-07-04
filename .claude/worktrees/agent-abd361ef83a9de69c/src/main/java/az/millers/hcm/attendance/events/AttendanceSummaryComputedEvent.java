package az.millers.hcm.attendance.events;

import java.util.UUID;

import az.millers.hcm.attendance.domain.DailySummary;

/**
 * M337: Published after AttendanceEngine computes a daily summary.
 *
 * <p>Triggers exception generation and notifications.
 */
public record AttendanceSummaryComputedEvent(DailySummary summary, UUID tenantId) {
}
