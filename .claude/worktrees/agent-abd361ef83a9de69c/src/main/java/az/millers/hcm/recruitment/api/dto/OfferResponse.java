package az.millers.hcm.recruitment.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.recruitment.domain.Offer;
import az.millers.hcm.recruitment.domain.OfferStatus;

public record OfferResponse(
        UUID id,
        String offerNo,
        UUID applicationId,
        BigDecimal proposedSalary,
        String currency,
        LocalDate proposedStartDate,
        String benefits,
        OfferStatus status,
        // M276 — approval workflow + salary exception flag
        UUID workflowInstanceId,
        boolean salaryException,
        OffsetDateTime sentAt,
        String sentBy,
        OffsetDateTime responseAt,
        String notes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static OfferResponse from(Offer o) {
        return new OfferResponse(
                o.getId(), o.getOfferNo(), o.getApplicationId(),
                o.getProposedSalary(), o.getCurrency(), o.getProposedStartDate(),
                o.getBenefits(), o.getStatus(),
                o.getWorkflowInstanceId(), o.isSalaryException(),
                o.getSentAt(), o.getSentBy(), o.getResponseAt(),
                o.getNotes(),
                o.getCreatedAt(), o.getUpdatedAt());
    }
}
