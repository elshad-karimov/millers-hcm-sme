package az.millers.hcm.compensation.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateSalaryChangeRequest(
        UUID employeeId,
        BigDecimal proposedSalary,
        String currency,
        UUID changeReasonId,
        LocalDate effectiveDate,
        String note
) {
}
