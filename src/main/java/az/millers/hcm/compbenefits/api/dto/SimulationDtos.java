package az.millers.hcm.compbenefits.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * M129 — wire DTOs for the bonus payout simulator.
 */
public final class SimulationDtos {

    private SimulationDtos() {}

    /** Request body — the knobs the SPA slides. */
    public record SimulationRequest(
            /** Pool override. Null falls back to the cycle's pool_total. */
            BigDecimal poolTotal,
            BigDecimal performanceWeight,
            BigDecimal tenureWeight,
            BigDecimal baseWeight,
            /** Lower bound per employee, % of base salary. Null/0 = no floor. */
            BigDecimal floorPercent,
            /** Upper bound per employee, % of base salary. Null/0 = no cap. */
            BigDecimal capPercent) {}

    public record AllocationRow(
            UUID employeeId,
            String employeeNo,
            String employeeName,
            BigDecimal baseSalary,
            BigDecimal performanceRating,
            int tenureMonths,
            BigDecimal score,
            BigDecimal weight,
            BigDecimal allocatedBonus,
            BigDecimal percentOfBase,
            boolean clamped) {}

    public record SimulationResponse(
            UUID cycleId,
            BigDecimal poolTotal,
            BigDecimal totalAllocated,
            BigDecimal residual,
            int eligibleCount,
            int clampedCount,
            List<AllocationRow> allocations) {}
}
