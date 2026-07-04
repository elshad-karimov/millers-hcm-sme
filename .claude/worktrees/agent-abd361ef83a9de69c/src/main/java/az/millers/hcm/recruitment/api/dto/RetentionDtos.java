package az.millers.hcm.recruitment.api.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** M294 — Recruitment PRD §46/§47 consent-retention + anonymisation DTOs. */
public final class RetentionDtos {

    private RetentionDtos() {}

    /** One candidate's standing against the retention window. */
    public record RetentionRow(
            UUID id,
            String candidateNo,
            String fullName,
            String email,
            LocalDate anchorDate,     // most recent consent / contact / created
            LocalDate retentionDate,  // anchor + retention months
            long daysUntilDue,        // negative = overdue
            boolean overdue) {}

    /** Candidates due now + approaching the window. */
    public record RetentionReport(
            int retentionMonths,
            int dueCount,
            int upcomingCount,
            List<RetentionRow> due,
            List<RetentionRow> upcoming) {}

    /** Outcome of a retention sweep. */
    public record SweepResult(
            boolean dryRun,
            int count,
            List<String> candidateNos) {}

    /** Outcome of anonymising one candidate. */
    public record AnonymizeResult(
            UUID candidateId,
            int educationDeleted,
            int experienceDeleted,
            int skillsDeleted,
            int notesDeleted,
            int documentsDeleted) {}
}
