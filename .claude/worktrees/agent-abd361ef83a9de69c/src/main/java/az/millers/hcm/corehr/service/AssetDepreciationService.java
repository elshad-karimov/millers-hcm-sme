package az.millers.hcm.corehr.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.api.dto.DepreciationResponse;
import az.millers.hcm.corehr.api.dto.DepreciationResponse.AssetDepreciation;
import az.millers.hcm.corehr.api.dto.DepreciationResponse.PeriodRow;
import az.millers.hcm.corehr.domain.EmployeeAsset;
import az.millers.hcm.corehr.repo.EmployeeAssetRepository;
import az.millers.hcm.corehr.service.DepreciationCalculator.Period;

/**
 * M128 — assembles the depreciation schedule for one asset by reading
 * the asset's configured cost / life / method and running them through
 * {@link DepreciationCalculator}.
 */
@Service
public class AssetDepreciationService {

    private final EmployeeAssetRepository assets;

    public AssetDepreciationService(EmployeeAssetRepository assets) {
        this.assets = assets;
    }

    @Transactional(readOnly = true)
    public AssetDepreciation scheduleFor(UUID assetId) {
        EmployeeAsset a = assets.findById(assetId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found: " + assetId));
        List<Period> periods = DepreciationCalculator.schedule(
                a.getPurchaseCost(),
                a.getPurchaseDate(),
                a.getUsefulLifeMonths(),
                a.getSalvageValue(),
                a.getDepreciationMethod(),
                a.getDecliningRatePercent());
        List<PeriodRow> rows = new ArrayList<>(periods.size());
        BigDecimal total = BigDecimal.ZERO;
        for (Period p : periods) {
            rows.add(new PeriodRow(p.period(), p.periodStart(),
                    p.openingValue(), p.depreciation(), p.closingValue()));
            total = total.add(p.depreciation());
        }
        BigDecimal bookToday = DepreciationCalculator.bookValueOn(
                periods, a.getPurchaseDate(), a.getPurchaseCost(),
                a.getSalvageValue(), LocalDate.now());
        return new AssetDepreciation(
                a.getId(), a.getAssetName(), a.getDepreciationMethod(),
                a.getPurchaseCost(), a.getPurchaseDate(),
                a.getUsefulLifeMonths(), a.getSalvageValue(),
                a.getDecliningRatePercent(),
                total, bookToday, rows);
    }
}
