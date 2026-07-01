package az.millers.hcm.compensation.api.dto;

import java.math.BigDecimal;

public record MarketComparisonDto(
    BigDecimal currentSalary,
    String gradeCode,
    String surveyProvider,
    int surveyYear,
    BigDecimal p25,
    BigDecimal p50,
    BigDecimal p75,
    BigDecimal p90,
    BigDecimal marketRatio,
    String positionVsMarket
) {}
