package az.millers.hcm.corehr.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import az.millers.hcm.corehr.domain.DepreciationMethod;

/**
 * M128 — wire shape of an asset's depreciation schedule.
 */
public final class DepreciationResponse {

    private DepreciationResponse() {}

    public record PeriodRow(
            int period,
            LocalDate periodStart,
            BigDecimal openingValue,
            BigDecimal depreciation,
            BigDecimal closingValue) {}

    public record AssetDepreciation(
            UUID assetId,
            String assetName,
            DepreciationMethod method,
            BigDecimal purchaseCost,
            LocalDate purchaseDate,
            Integer usefulLifeMonths,
            BigDecimal salvageValue,
            BigDecimal decliningRatePercent,
            BigDecimal totalDepreciation,
            BigDecimal bookValueToday,
            List<PeriodRow> schedule) {}
}
