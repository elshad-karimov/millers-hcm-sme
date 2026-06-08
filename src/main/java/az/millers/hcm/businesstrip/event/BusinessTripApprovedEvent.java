package az.millers.hcm.businesstrip.event;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Published when a business-trip request is approved through the workflow
 * (M199 / PRD §8.6.7 AC: "notifies Finance for disbursement").
 */
public record BusinessTripApprovedEvent(
        UUID tripId,
        String tripNo,
        UUID employeeId,
        String destinationCity,
        String destinationCountry,
        LocalDate startDate,
        LocalDate endDate,
        int totalDays,
        BigDecimal approvedAdvance,
        String currency) {
}
