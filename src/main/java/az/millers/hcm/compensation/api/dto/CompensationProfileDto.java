package az.millers.hcm.compensation.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * M360 — Employee compensation profile projection.
 * Aggregates current salary, grade, band, compa-ratio, range penetration, and history.
 */
public record CompensationProfileDto(
    CurrentSalaryInfo currentSalary,
    GradeInfo grade,
    BandInfo band,
    BigDecimal compaRatio,
    BigDecimal rangePenetration,
    String rangePositionLabel,
    List<SalaryHistoryItem> salaryHistory
) {
    public record CurrentSalaryInfo(
        BigDecimal monthlyBaseSalary,
        String currency,
        LocalDate effectiveFrom
    ) {}

    public record GradeInfo(
        String code,
        String name,
        BigDecimal minSalary,
        BigDecimal maxSalary
    ) {}

    public record BandInfo(
        String code,
        String name,
        BigDecimal minSalary,
        BigDecimal midSalary,
        BigDecimal maxSalary,
        BigDecimal q1Salary,
        BigDecimal q3Salary
    ) {}

    public record SalaryHistoryItem(
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        BigDecimal monthlyBaseSalary,
        String currency,
        String reason
    ) {}
}
