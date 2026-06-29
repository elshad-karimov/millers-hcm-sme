package az.millers.hcm.leave.api.dto;

import java.util.List;

public record UnpaidDeductionSyncResult(
        int year,
        int month,
        int workingDaysPerMonth,
        int created,
        int skipped,
        List<String> details
) {}
