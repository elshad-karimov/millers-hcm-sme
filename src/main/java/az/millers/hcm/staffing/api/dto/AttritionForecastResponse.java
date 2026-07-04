package az.millers.hcm.staffing.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import az.millers.hcm.staffing.domain.AttritionForecast;

public record AttritionForecastResponse(
        UUID id,
        UUID planId,
        UUID orgUnitId,
        LocalDate forecastDate,
        BigDecimal expectedExits,
        String basis,
        String detail
) {
    public static AttritionForecastResponse from(AttritionForecast a) {
        return new AttritionForecastResponse(
                a.getId(),
                a.getPlanId(),
                a.getOrgUnitId(),
                a.getForecastDate(),
                a.getExpectedExits(),
                a.getBasis(),
                a.getDetail()
        );
    }
}
