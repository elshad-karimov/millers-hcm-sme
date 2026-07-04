package az.millers.hcm.leave.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record LeaveLiabilityReport(
        int year,
        int workingDaysPerMonth,
        BigDecimal totalLiability,
        List<LeaveLiabilityRow> rows
) {}
