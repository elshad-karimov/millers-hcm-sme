package az.millers.hcm.organization.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import az.millers.hcm.organization.domain.OrgUnitDocument;

public class OrgUnitDocumentDtos {

    public record OrgUnitDocumentRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 64) String docType,
            @Size(max = 400) String documentRef,
            LocalDate issuedDate,
            LocalDate expiryDate,
            /** Employee responsible for this document; receives expiry alerts. */
            UUID responsibleEmployeeId,
            String notes) {}

    public record OrgUnitDocumentResponse(
            UUID id,
            UUID orgUnitId,
            String title,
            String docType,
            String documentRef,
            LocalDate issuedDate,
            LocalDate expiryDate,
            UUID responsibleEmployeeId,
            String notes,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            String createdBy,
            String updatedBy) {

        public static OrgUnitDocumentResponse from(OrgUnitDocument d) {
            return new OrgUnitDocumentResponse(
                    d.getId(), d.getOrgUnitId(), d.getTitle(), d.getDocType(),
                    d.getDocumentRef(), d.getIssuedDate(), d.getExpiryDate(),
                    d.getResponsibleEmployeeId(), d.getNotes(),
                    d.getCreatedAt(), d.getUpdatedAt(), d.getCreatedBy(), d.getUpdatedBy());
        }
    }

    private OrgUnitDocumentDtos() {}
}
