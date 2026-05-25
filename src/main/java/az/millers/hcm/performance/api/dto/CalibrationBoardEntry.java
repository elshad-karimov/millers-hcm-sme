package az.millers.hcm.performance.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CalibrationBoardEntry(
        UUID reviewId,
        UUID employeeId,
        String employeeName,
        String department,
        UUID managerId,
        BigDecimal selfRating,
        BigDecimal managerRating,
        BigDecimal finalRating,
        String finalBand,
        String recommendation,
        BigDecimal bonusPercent,
        String calibrationNotes) {
}
