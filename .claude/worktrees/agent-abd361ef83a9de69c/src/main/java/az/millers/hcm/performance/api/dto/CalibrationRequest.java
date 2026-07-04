package az.millers.hcm.performance.api.dto;

import java.math.BigDecimal;

public record CalibrationRequest(
        BigDecimal finalRating,
        String finalBand,
        String recommendation,
        BigDecimal bonusPercent,
        String calibrationNotes) {
}
