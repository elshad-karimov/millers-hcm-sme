package az.millers.hcm.payroll.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CostAllocationRequest(
    List<AllocationItem> allocations,
    LocalDate effectiveFrom
) {
    public record AllocationItem(String costCenterCode, BigDecimal allocationPct) {}
}
