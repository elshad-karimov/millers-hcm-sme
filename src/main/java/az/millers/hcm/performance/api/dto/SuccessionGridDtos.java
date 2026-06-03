package az.millers.hcm.performance.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * DTOs for the M92 9-box succession grid.
 *
 * <p>The grid plots {@code finalRating} on the performance axis and
 * {@code potentialRating} on the potential axis. Both are bucketed into
 * three bands ({@link Band#LOW}, {@link Band#MID}, {@link Band#HIGH}),
 * giving a 3×3 = 9-cell matrix. Each cell carries the list of employees
 * who fall into it so the UI can render a drill-down per cell.
 */
public final class SuccessionGridDtos {
    private SuccessionGridDtos() {}

    /** Performance / potential bucket. Boundaries: 1-2.49 LOW, 2.5-3.99 MID, 4-5 HIGH. */
    public enum Band {
        LOW,
        MID,
        HIGH;

        /** @return the band for a 1-5 rating. */
        public static Band of(BigDecimal rating) {
            if (rating == null) {
                throw new IllegalArgumentException("Band requires a non-null rating");
            }
            double v = rating.doubleValue();
            if (v < 2.5) return LOW;
            if (v < 4.0) return MID;
            return HIGH;
        }
    }

    /** One employee placed in a cell. */
    public record GridEmployee(
            UUID reviewId,
            UUID employeeId,
            String employeeName,
            String department,
            BigDecimal performanceRating,
            BigDecimal potentialRating,
            String recommendation) {}

    /** One cell of the 9-box. The label is the HR-standard archetype name. */
    public record GridCell(
            Band performance,
            Band potential,
            String label,
            int count,
            List<GridEmployee> employees) {}

    /** Top-level response for one cycle. */
    public record SuccessionGrid(
            UUID cycleId,
            String cycleName,
            int totalReviews,
            int placedReviews,
            int missingPerformance,
            int missingPotential,
            List<GridCell> cells) {}

    /** Inbound payload to set/update an employee's potential rating during calibration. */
    public record PotentialRatingRequest(
            BigDecimal potentialRating,
            String potentialNotes) {}
}
