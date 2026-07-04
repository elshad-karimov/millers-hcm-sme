package az.millers.hcm.recruitment.api.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Min;

import az.millers.hcm.recruitment.domain.ApplicationStage;
import az.millers.hcm.recruitment.domain.StageSla;

/** M288 — Recruitment PRD §14/§43 stage-SLA DTOs. */
public final class StageSlaDtos {

    private StageSlaDtos() {}

    /** Edit one stage's SLA (days + owner). */
    public record SlaConfigUpdate(
            @Min(1) int slaDays,
            String ownerRole,
            Boolean active) {}

    public record SlaConfig(
            UUID id,
            ApplicationStage stage,
            int slaDays,
            String ownerRole,
            boolean active) {

        public static SlaConfig from(StageSla s) {
            return new SlaConfig(s.getId(), s.getStage(), s.getSlaDays(),
                    s.getOwnerRole(), s.isActive());
        }
    }

    /** One application past (or near) its current stage's SLA. */
    public record SlaBreachRow(
            UUID applicationId,
            String applicationNo,
            UUID candidateId,
            String candidateName,
            String vacancyTitle,
            ApplicationStage stage,
            String ownerRole,
            long daysInStage,
            int slaDays,
            long daysOver,
            String severity) {}   // OVERDUE | DUE_SOON

    public record SlaBreachReport(
            int overdueCount,
            int dueSoonCount,
            List<SlaBreachRow> rows) {}
}
