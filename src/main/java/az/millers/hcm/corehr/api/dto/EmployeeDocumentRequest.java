package az.millers.hcm.corehr.api.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import az.millers.hcm.corehr.domain.EmployeeDocumentType;

public record EmployeeDocumentRequest(
        @NotNull EmployeeDocumentType documentType,
        UUID attachmentId,
        @Size(max = 255) String title,
        LocalDate expiryDate,
        Boolean restricted,
        @Size(max = 4000) String notes) {
}
