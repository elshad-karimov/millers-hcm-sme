package az.millers.hcm.attendance.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.attendance.domain.AttendanceException;
import az.millers.hcm.attendance.domain.ExceptionConfig;

public class ExceptionDtos {

    public record ExceptionConfigRequest(
            String exceptionType,
            int thresholdMinutes,
            String severity,
            boolean enabled,
            boolean autoNotify) {
    }

    public record ExceptionConfigResponse(
            UUID id,
            UUID tenantId,
            String exceptionType,
            int thresholdMinutes,
            String severity,
            boolean enabled,
            boolean autoNotify,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {

        public static ExceptionConfigResponse from(ExceptionConfig entity) {
            return new ExceptionConfigResponse(
                    entity.getId(),
                    entity.getTenantId(),
                    entity.getExceptionType(),
                    entity.getThresholdMinutes(),
                    entity.getSeverity(),
                    entity.isEnabled(),
                    entity.isAutoNotify(),
                    entity.getCreatedAt(),
                    entity.getUpdatedAt());
        }
    }

    public record AttendanceExceptionResponse(
            UUID id,
            UUID tenantId,
            UUID employeeId,
            LocalDate workDate,
            UUID summaryId,
            String exceptionType,
            String severity,
            int thresholdMinutes,
            int actualMinutes,
            String status,
            String acknowledgedBy,
            OffsetDateTime acknowledgedAt,
            String resolvedBy,
            OffsetDateTime resolvedAt,
            String notes,
            OffsetDateTime createdAt) {

        public static AttendanceExceptionResponse from(AttendanceException entity) {
            return new AttendanceExceptionResponse(
                    entity.getId(),
                    entity.getTenantId(),
                    entity.getEmployeeId(),
                    entity.getWorkDate(),
                    entity.getSummaryId(),
                    entity.getExceptionType(),
                    entity.getSeverity(),
                    entity.getThresholdMinutes(),
                    entity.getActualMinutes(),
                    entity.getStatus(),
                    entity.getAcknowledgedBy(),
                    entity.getAcknowledgedAt(),
                    entity.getResolvedBy(),
                    entity.getResolvedAt(),
                    entity.getNotes(),
                    entity.getCreatedAt());
        }
    }

    public record ExceptionDecision(String notes) {
    }
}
