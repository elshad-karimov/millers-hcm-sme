package az.millers.hcm.ehs.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.ehs.domain.ReturnToWorkPlan;
import az.millers.hcm.ehs.domain.ReturnToWorkStatus;

public class ReturnToWorkDtos {

    public record CreateReturnToWorkRequest(
            UUID injuryReportId,
            UUID employeeId,
            LocalDate medicalClearanceDate,
            String restrictions,
            String modifiedSchedule
    ) {}

    public record UpdateReturnToWorkRequest(
            LocalDate medicalClearanceDate,
            String restrictions,
            String modifiedSchedule,
            ReturnToWorkStatus status
    ) {}

    public record SetApprovalRequest(
            boolean approved
    ) {}

    public record ReturnToWorkResponse(
            UUID id,
            String tenantId,
            UUID injuryReportId,
            UUID employeeId,
            String employeeName,
            LocalDate medicalClearanceDate,
            String restrictions,
            String modifiedSchedule,
            Boolean managerApproved,
            Boolean hrApproved,
            ReturnToWorkStatus status,
            OffsetDateTime closedAt,
            String createdBy,
            OffsetDateTime createdAt,
            String updatedBy,
            OffsetDateTime updatedAt
    ) {
        public static ReturnToWorkResponse from(ReturnToWorkPlan plan, String employeeName) {
            return new ReturnToWorkResponse(
                    plan.getId(),
                    plan.getTenantId(),
                    plan.getInjuryReportId(),
                    plan.getEmployeeId(),
                    employeeName,
                    plan.getMedicalClearanceDate(),
                    plan.getRestrictions(),
                    plan.getModifiedSchedule(),
                    plan.getManagerApproved(),
                    plan.getHrApproved(),
                    plan.getStatus(),
                    plan.getClosedAt(),
                    plan.getCreatedBy(),
                    plan.getCreatedAt(),
                    plan.getUpdatedBy(),
                    plan.getUpdatedAt()
            );
        }
    }
}
