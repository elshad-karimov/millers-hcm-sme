package az.millers.hcm.performance.api.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CalibrationBoardResponse(
        UUID cycleId,
        String cycleName,
        int totalReviews,
        Map<String, Long> ratingDistribution,
        List<CalibrationBoardEntry> entries) {
}
