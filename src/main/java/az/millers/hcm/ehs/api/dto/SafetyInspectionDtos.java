package az.millers.hcm.ehs.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import az.millers.hcm.ehs.domain.FindingStatus;
import az.millers.hcm.ehs.domain.InspectionFinding;
import az.millers.hcm.ehs.domain.InspectionStatus;
import az.millers.hcm.ehs.domain.SafetyInspection;

public class SafetyInspectionDtos {

    public record CreateSafetyInspectionRequest(
            UUID workLocationId,
            LocalDate inspectionDate,
            String inspectorUsername,
            String title,
            String notes,
            List<FindingDto> findings
    ) {}

    public record UpdateSafetyInspectionRequest(
            String title,
            String notes,
            List<FindingDto> findings
    ) {}

    public record FindingDto(
            String itemLabel,
            FindingStatus findingStatus,
            String notes,
            UUID correctiveActionId
    ) {}

    public record SafetyInspectionResponse(
            UUID id,
            String tenantId,
            UUID workLocationId,
            LocalDate inspectionDate,
            String inspectorUsername,
            String title,
            Integer overallScore,
            InspectionStatus status,
            String notes,
            String createdBy,
            OffsetDateTime createdAt,
            String updatedBy,
            OffsetDateTime updatedAt
    ) {
        public static SafetyInspectionResponse from(SafetyInspection inspection) {
            return new SafetyInspectionResponse(
                    inspection.getId(),
                    inspection.getTenantId(),
                    inspection.getWorkLocationId(),
                    inspection.getInspectionDate(),
                    inspection.getInspectorUsername(),
                    inspection.getTitle(),
                    inspection.getOverallScore(),
                    inspection.getStatus(),
                    inspection.getNotes(),
                    inspection.getCreatedBy(),
                    inspection.getCreatedAt(),
                    inspection.getUpdatedBy(),
                    inspection.getUpdatedAt()
            );
        }
    }

    public record InspectionFindingResponse(
            UUID id,
            UUID inspectionId,
            String itemLabel,
            FindingStatus findingStatus,
            String notes,
            UUID correctiveActionId
    ) {
        public static InspectionFindingResponse from(InspectionFinding f) {
            return new InspectionFindingResponse(
                    f.getId(),
                    f.getInspectionId(),
                    f.getItemLabel(),
                    f.getFindingStatus(),
                    f.getNotes(),
                    f.getCorrectiveActionId()
            );
        }
    }
}
