package az.millers.hcm.compensation.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.compensation.domain.CompensationException;
import az.millers.hcm.compensation.domain.CompensationExceptionSeverity;
import az.millers.hcm.compensation.domain.CompensationExceptionSource;
import az.millers.hcm.compensation.domain.CompensationExceptionStatus;
import az.millers.hcm.compensation.domain.CompensationExceptionType;

public record CompensationExceptionDto(
        UUID id,
        UUID employeeId,
        CompensationExceptionSource sourceType,
        UUID sourceId,
        CompensationExceptionType exceptionType,
        CompensationExceptionSeverity severity,
        CompensationExceptionStatus status,
        String reason,
        OffsetDateTime raisedAt,
        String resolvedBy,
        OffsetDateTime resolvedAt
) {
    public static CompensationExceptionDto from(CompensationException e) {
        return new CompensationExceptionDto(
                e.getId(),
                e.getEmployeeId(),
                e.getSourceType(),
                e.getSourceId(),
                e.getExceptionType(),
                e.getSeverity(),
                e.getStatus(),
                e.getReason(),
                e.getRaisedAt(),
                e.getResolvedBy(),
                e.getResolvedAt()
        );
    }
}
