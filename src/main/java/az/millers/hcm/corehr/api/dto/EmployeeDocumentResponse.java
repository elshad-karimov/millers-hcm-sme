package az.millers.hcm.corehr.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.corehr.domain.EmployeeDocument;
import az.millers.hcm.corehr.domain.EmployeeDocumentType;

public record EmployeeDocumentResponse(
        UUID id,
        UUID employeeId,
        EmployeeDocumentType documentType,
        UUID attachmentId,
        String title,
        LocalDate expiryDate,
        boolean restricted,
        String notes,
        OffsetDateTime createdAt,
        String createdBy,
        OffsetDateTime updatedAt,
        String updatedBy) {

    public static EmployeeDocumentResponse from(EmployeeDocument d) {
        return new EmployeeDocumentResponse(
                d.getId(),
                d.getEmployeeId(),
                d.getDocumentType(),
                d.getAttachmentId(),
                d.getTitle(),
                d.getExpiryDate(),
                d.isRestricted(),
                d.getNotes(),
                d.getCreatedAt(),
                d.getCreatedBy(),
                d.getUpdatedAt(),
                d.getUpdatedBy());
    }
}
