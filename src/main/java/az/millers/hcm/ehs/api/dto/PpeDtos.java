package az.millers.hcm.ehs.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.ehs.domain.PpeAssignment;
import az.millers.hcm.ehs.domain.PpeItem;
import az.millers.hcm.ehs.domain.PpeType;

public class PpeDtos {

    public record IssuePpeRequest(
            UUID employeeId,
            UUID ppeItemId,
            LocalDate issuedAt,
            LocalDate expiryDate,
            String conditionAtIssue,
            String notes
    ) {}

    public record ReturnPpeRequest(
            LocalDate returnedAt,
            String conditionAtReturn
    ) {}

    public record PpeItemResponse(
            UUID id,
            String tenantId,
            String code,
            String name,
            PpeType ppeType,
            Integer defaultExpiryMonths,
            Boolean active,
            String createdBy,
            OffsetDateTime createdAt,
            String updatedBy,
            OffsetDateTime updatedAt
    ) {
        public static PpeItemResponse from(PpeItem item) {
            return new PpeItemResponse(
                    item.getId(),
                    item.getTenantId(),
                    item.getCode(),
                    item.getName(),
                    item.getPpeType(),
                    item.getDefaultExpiryMonths(),
                    item.getActive(),
                    item.getCreatedBy(),
                    item.getCreatedAt(),
                    item.getUpdatedBy(),
                    item.getUpdatedAt()
            );
        }
    }

    public record PpeAssignmentResponse(
            UUID id,
            String tenantId,
            UUID employeeId,
            String employeeName,
            UUID ppeItemId,
            String ppeItemName,
            LocalDate issuedAt,
            LocalDate expiryDate,
            LocalDate returnedAt,
            String conditionAtIssue,
            String conditionAtReturn,
            String notes,
            String createdBy,
            OffsetDateTime createdAt,
            String updatedBy,
            OffsetDateTime updatedAt
    ) {}
}
