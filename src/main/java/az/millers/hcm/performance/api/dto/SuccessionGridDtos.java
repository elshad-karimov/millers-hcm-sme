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

    /** Readiness tier for a successor (M94). */
    public enum Readiness {
        /** {@code Star} — HIGH/HIGH. Ready to step up immediately. */
        READY_NOW,
        /** {@code Future Star} / {@code High Performer} — promotable in 1-2 yrs. */
        READY_SOON,
        /** {@code Core Player} / {@code Trusted Pro} — long-term bench. */
        READY_LONG_TERM,
        /** Anything else placed on the grid (LOW perf or LOW pot). */
        UNDER_DEVELOPMENT
    }

    /** One row of the bench-depth report — counts per manager. */
    public record BenchRow(
            UUID managerId,
            String managerName,
            int totalReports,
            int placedReports,
            int readyNow,
            int readySoon,
            int readyLongTerm,
            int underDevelopment) {}

    /** Bench-depth response for one cycle. */
    public record BenchReport(
            UUID cycleId,
            String cycleName,
            int totalManagers,
            List<BenchRow> rows) {}
}
