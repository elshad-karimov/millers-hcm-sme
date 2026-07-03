package az.millers.hcm.performance.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.performance.domain.GoalProgressUpdate;

/** M392 — one §6.4 progress-trail row. */
public record GoalProgressUpdateResponse(
        UUID id,
        BigDecimal oldProgress,
        BigDecimal newProgress,
        String oldStatus,
        String newStatus,
        String note,
        String recordedBy,
        OffsetDateTime recordedAt) {

    public static GoalProgressUpdateResponse from(GoalProgressUpdate u) {
        return new GoalProgressUpdateResponse(u.getId(), u.getOldProgress(), u.getNewProgress(),
                u.getOldStatus(), u.getNewStatus(), u.getNote(), u.getRecordedBy(), u.getRecordedAt());
    }
}
