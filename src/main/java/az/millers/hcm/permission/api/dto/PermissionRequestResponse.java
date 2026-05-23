package az.millers.hcm.permission.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.permission.domain.PermissionRequest;
import az.millers.hcm.permission.domain.PermissionRequestStatus;

public record PermissionRequestResponse(
        UUID id,
        String requestNo,
        UUID employeeId,
        UUID permissionTypeId,
        LocalDate permissionDate,
        LocalTime startTime,
        LocalTime endTime,
        BigDecimal durationHours,
        String reason,
        String attachmentUrl,
        PermissionRequestStatus status,
        UUID workflowInstanceId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String createdBy) {

    public static PermissionRequestResponse from(PermissionRequest r) {
        return new PermissionRequestResponse(
                r.getId(), r.getRequestNo(), r.getEmployeeId(), r.getPermissionTypeId(),
                r.getPermissionDate(), r.getStartTime(), r.getEndTime(), r.getDurationHours(),
                r.getReason(), r.getAttachmentUrl(),
                r.getStatus(), r.getWorkflowInstanceId(),
                r.getCreatedAt(), r.getUpdatedAt(), r.getCreatedBy());
    }
}
