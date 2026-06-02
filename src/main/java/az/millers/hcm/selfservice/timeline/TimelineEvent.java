package az.millers.hcm.selfservice.timeline;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One row in an employee's lifecycle timeline (M76 / P2-27/28). Pulled from
 * many sources (audit log, lifecycle events, leave / business-trip / permission
 * submissions, contracts, disciplinary actions, probation reviews) and merged
 * chronologically by {@link EmployeeTimelineService}.
 *
 * <p>The {@code kind} enum is the discriminator the frontend uses to pick an
 * icon + colour. {@code title} is human-readable. {@code detail} is optional
 * narrative. {@code actor} is the user / system that triggered the event.
 * {@code referenceId} optionally links back to the originating row so the UI
 * can deep-link.
 */
public record TimelineEvent(
        OffsetDateTime at,
        TimelineKind kind,
        String title,
        String detail,
        String actor,
        UUID referenceId) {

    public enum TimelineKind {
        HIRE,
        STATUS_CHANGE,
        CONTRACT_SIGNED,
        CONTRACT_ENDED,
        LEAVE_REQUEST,
        BUSINESS_TRIP,
        PERMISSION,
        DISCIPLINARY,
        PROBATION_REVIEW,
        REWARD,
        TERMINATION,
        AUDIT_OTHER
    }

    public static TimelineEvent of(OffsetDateTime at, TimelineKind kind,
                                    String title, String detail, String actor,
                                    UUID referenceId) {
        return new TimelineEvent(at, kind, title, detail, actor, referenceId);
    }
}
