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
        String calibrationNotes,
        /** M93 — potential rating feeds the 9-box succession grid (M92). */
        BigDecimal potentialRating,
        /** M93 — rationale for the potential rating. */
        String potentialNotes,
        /** M121 — true once a calibration session has sealed this row. */
        boolean calibrationLocked) {
}
