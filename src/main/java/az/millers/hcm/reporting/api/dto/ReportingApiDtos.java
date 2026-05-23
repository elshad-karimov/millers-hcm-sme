package az.millers.hcm.reporting.api.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import az.millers.hcm.reporting.domain.ReportDefinition;
import az.millers.hcm.reporting.domain.ReportFormat;
import az.millers.hcm.reporting.domain.ReportRun;
import az.millers.hcm.reporting.domain.ReportRunStatus;
import az.millers.hcm.reporting.domain.ReportSchedule;
import az.millers.hcm.reporting.domain.ReportType;
import az.millers.hcm.reporting.domain.TriggerSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class ReportingApiDtos {

    private ReportingApiDtos() {}

    // ---- Definition ----

    public record DefinitionRequest(
            @NotBlank String name,
            @NotNull ReportType reportType,
            ReportFormat defaultFormat,
            Map<String, Object> parameters,
            String description,
            Boolean active) {}

    public record DefinitionResponse(
            UUID id,
            String definitionNo,
            String name,
            ReportType reportType,
            ReportFormat defaultFormat,
            Map<String, Object> parameters,
            String description,
            boolean active,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            String createdBy,
            String updatedBy) {

        public static DefinitionResponse from(ReportDefinition d) {
            return new DefinitionResponse(
                    d.getId(), d.getDefinitionNo(), d.getName(),
                    d.getReportType(), d.getDefaultFormat(),
                    d.getParameters(), d.getDescription(), d.isActive(),
                    d.getCreatedAt(), d.getUpdatedAt(),
                    d.getCreatedBy(), d.getUpdatedBy());
        }
    }

    // ---- Schedule ----

    public record ScheduleRequest(
            @NotBlank String name,
            @NotNull UUID definitionId,
            @NotBlank String cron,
            String recipients,
            az.millers.hcm.reporting.domain.WebhookType webhookType,
            String webhookUrl,
            Boolean active) {}

    public record ScheduleUpdateRequest(
            String name,
            String cron,
            String recipients,
            az.millers.hcm.reporting.domain.WebhookType webhookType,
            String webhookUrl,
            Boolean active) {}

    public record ScheduleResponse(
            UUID id,
            String scheduleNo,
            String name,
            UUID definitionId,
            String cron,
            String recipients,
            az.millers.hcm.reporting.domain.WebhookType webhookType,
            String webhookUrl,
            boolean active,
            OffsetDateTime lastRunAt,
            OffsetDateTime nextRunAt,
            String lastStatus,
            OffsetDateTime createdAt,
            String createdBy) {

        public static ScheduleResponse from(ReportSchedule s) {
            return new ScheduleResponse(
                    s.getId(), s.getScheduleNo(), s.getName(),
                    s.getDefinitionId(), s.getCron(), s.getRecipients(),
                    s.getWebhookType(), s.getWebhookUrl(), s.isActive(),
                    s.getLastRunAt(), s.getNextRunAt(), s.getLastStatus(),
                    s.getCreatedAt(), s.getCreatedBy());
        }
    }

    // ---- Run ----

    public record RunResponse(
            UUID id,
            String runNo,
            UUID definitionId,
            UUID scheduleId,
            ReportType reportType,
            ReportFormat format,
            Map<String, Object> parameters,
            UUID attachmentId,
            String fileName,
            Long sizeBytes,
            ReportRunStatus status,
            String errorMessage,
            OffsetDateTime startedAt,
            OffsetDateTime finishedAt,
            String triggeredBy,
            TriggerSource triggerSource,
            az.millers.hcm.reporting.domain.EmailStatus emailStatus,
            String emailRecipients,
            OffsetDateTime emailSentAt,
            String emailError,
            az.millers.hcm.reporting.domain.WebhookStatus webhookStatus,
            String webhookTarget,
            OffsetDateTime webhookSentAt,
            String webhookError) {

        public static RunResponse from(ReportRun r) {
            return new RunResponse(
                    r.getId(), r.getRunNo(),
                    r.getDefinitionId(), r.getScheduleId(),
                    r.getReportType(), r.getFormat(),
                    r.getParameters(),
                    r.getAttachmentId(), r.getFileName(), r.getSizeBytes(),
                    r.getStatus(), r.getErrorMessage(),
                    r.getStartedAt(), r.getFinishedAt(),
                    r.getTriggeredBy(), r.getTriggerSource(),
                    r.getEmailStatus(), r.getEmailRecipients(),
                    r.getEmailSentAt(), r.getEmailError(),
                    r.getWebhookStatus(), r.getWebhookTarget(),
                    r.getWebhookSentAt(), r.getWebhookError());
        }
    }
}
