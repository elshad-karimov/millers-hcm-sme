package az.millers.hcm.recruitment.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTOs for the M88 recruitment analytics surface — funnel counts,
 * time-to-hire, source effectiveness, stale outreach.
 */
public final class RecruitmentAnalyticsDtos {
    private RecruitmentAnalyticsDtos() {}

    public record FunnelRow(
            String stage,
            long applications,
            /** Conversion vs. the prior stage. Null on the first stage. */
            BigDecimal conversionPct) {}

    public record FunnelReport(
            LocalDate from, LocalDate to,
            long totalCreated,
            long hired, long rejected, long withdrawn,
            List<FunnelRow> rows) {}

    public record TimeToHireRow(
            String fromStage, String toStage,
            long transitions,
            BigDecimal avgDays,
            BigDecimal medianDays) {}

    public record TimeToHireReport(
            LocalDate from, LocalDate to,
            BigDecimal avgDaysCreatedToHired,
            BigDecimal medianDaysCreatedToHired,
            List<TimeToHireRow> stageTransitions) {}

    public record SourceRow(
            String source,
            long applications,
            long hires,
            BigDecimal hireRatePct,
            BigDecimal avgTimeToHireDays) {}

    public record SourceReport(
            LocalDate from, LocalDate to,
            List<SourceRow> rows) {}

    public record StaleCandidateRow(
            UUID candidateId,
            String candidateNo,
            String fullName,
            String email,
            String poolStatus,
            OffsetDateTime lastContactedAt,
            long daysSinceContact) {}

    public record StaleReport(
            int thresholdDays,
            long total,
            List<StaleCandidateRow> rows) {}

    /**
     * Lightweight stale-pool summary (M89) for the home-dashboard widget.
     * Counts are bucketed by how long since last contact so the tile can
     * colour-code urgency without paging the full list.
     */
    public record StaleSummary(
            int thresholdDays,
            long total,
            long bucket30to59,
            long bucket60to89,
            long bucket90plus,
            long neverContacted) {}
}
