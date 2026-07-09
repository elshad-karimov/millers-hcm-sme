package az.millers.hcm.analytics.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * M475 — Executive summary DTO.
 */
public record ExecutiveSummary(
        HeadcountTrendSummary headcountTrend,
        BigDecimal turnover12m,
        PayrollCostTrendSummary payrollCostTrend,
        Integer enps,
        List<ComplianceDeadlineItem> upcomingComplianceDeadlines,
        Long attritionHighRiskCount
) {
    public record HeadcountTrendSummary(Long current, Long previousMonth, String trend) {}
    public record PayrollCostTrendSummary(BigDecimal currentMonth, BigDecimal previousMonth, String trend) {}
    public record ComplianceDeadlineItem(String title, String dueDate, String status) {}
}
