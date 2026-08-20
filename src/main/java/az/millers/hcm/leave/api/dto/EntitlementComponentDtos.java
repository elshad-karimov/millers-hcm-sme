package az.millers.hcm.leave.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import az.millers.hcm.leave.domain.EntitlementComponentCode;
import az.millers.hcm.leave.domain.EntitlementComponentSource;
import az.millers.hcm.leave.domain.LeaveEntitlementComponent;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** M151 — request/response shapes for the itemised entitlement breakdown. */
public final class EntitlementComponentDtos {

    private EntitlementComponentDtos() {}

    /** One line of the breakdown. */
    public record ComponentResponse(
            UUID id,
            EntitlementComponentCode componentCode,
            BigDecimal days,
            EntitlementComponentSource source,
            String basis,
            OffsetDateTime computedAt,
            String updatedBy) {

        public static ComponentResponse from(LeaveEntitlementComponent c) {
            return new ComponentResponse(
                    c.getId(), c.getComponentCode(), c.getDays(), c.getSource(),
                    c.getBasis(), c.getComputedAt(), c.getUpdatedBy());
        }
    }

    /**
     * The breakdown plus its total. The total is returned alongside rather
     * than left for the client to add up, so the figure on screen is the same
     * one that was written into the balance.
     */
    public record BreakdownResponse(
            UUID employeeId,
            UUID leaveTypeId,
            int year,
            BigDecimal totalDays,
            List<ComponentResponse> components) {}

    /**
     * Set or clear a manual component.
     *
     * @param days  null clears the override and hands the component back to
     *              the resolvers
     * @param basis required whenever days is present — a hand-entered
     *              component has no rule to point at, so it carries its own
     *              justification
     */
    public record ManualComponentRequest(
            @NotNull EntitlementComponentCode componentCode,
            @DecimalMin(value = "0", message = "days must be ≥ 0")
            @DecimalMax(value = "365", message = "days must be ≤ 365")
            BigDecimal days,
            @Size(max = 500) String basis) {}
}
