package az.millers.hcm.performance.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import az.millers.hcm.performance.service.CalibrationBoardMath;

public record CalibrationBoardResponse(
        UUID cycleId,
        String cycleName,
        int totalReviews,
        Map<String, Long> ratingDistribution,
        /** M121 — band → {actualCount, actualPct, targetPct, delta}. */
        Map<String, CalibrationBoardMath.BoardCell> boardCells,
        /** M121 — raw band → targetPct map. Convenience for the chart legend. */
        Map<String, BigDecimal> targetDistribution,
        List<CalibrationBoardEntry> entries) {
}
