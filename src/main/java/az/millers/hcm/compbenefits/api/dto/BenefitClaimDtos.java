package az.millers.hcm.compbenefits.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import az.millers.hcm.compbenefits.domain.BenefitClaim;
import az.millers.hcm.compbenefits.domain.BenefitClaimItem;
import az.millers.hcm.compbenefits.domain.BenefitClaimStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** DTOs for benefit claims (HCM_11 M381/M382). */
public final class BenefitClaimDtos {

    private BenefitClaimDtos() {}

    public record ItemRequest(
            LocalDate serviceDate,
            @NotBlank String description,
            @PositiveOrZero BigDecimal amount) {
    }

    public record ClaimRequest(
            @NotNull UUID employeeId,
            UUID enrollmentId,
            UUID planId,
            @NotNull LocalDate claimDate,
            String currency,
            String description,
            @NotNull List<ItemRequest> items) {
    }

    public record ReviewRequest(BigDecimal approvedAmount, String reviewNotes) {}

    public record PayRequest(String paymentReference) {}

    public record ItemResponse(
            UUID id, LocalDate serviceDate, String description, BigDecimal amount) {
        public static ItemResponse from(BenefitClaimItem i) {
            return new ItemResponse(i.getId(), i.getServiceDate(), i.getDescription(), i.getAmount());
        }
    }

    public record ClaimResponse(
            UUID id,
            String claimNo,
            UUID employeeId,
            String employeeName,
            UUID enrollmentId,
            UUID planId,
            String planName,
            LocalDate claimDate,
            String currency,
            BigDecimal totalAmount,
            BigDecimal approvedAmount,
            BenefitClaimStatus status,
            String description,
            String submittedBy,
            OffsetDateTime submittedAt,
            String reviewedBy,
            OffsetDateTime reviewedAt,
            String reviewNotes,
            String paidBy,
            OffsetDateTime paidAt,
            String paymentReference,
            List<ItemResponse> items,
            OffsetDateTime createdAt) {

        public static ClaimResponse from(BenefitClaim c, String employeeName, String planName,
                                         List<BenefitClaimItem> items) {
            return new ClaimResponse(
                    c.getId(), c.getClaimNo(), c.getEmployeeId(), employeeName, c.getEnrollmentId(),
                    c.getPlanId(), planName, c.getClaimDate(), c.getCurrency(), c.getTotalAmount(),
                    c.getApprovedAmount(), c.getStatus(), c.getDescription(), c.getSubmittedBy(),
                    c.getSubmittedAt(), c.getReviewedBy(), c.getReviewedAt(), c.getReviewNotes(),
                    c.getPaidBy(), c.getPaidAt(), c.getPaymentReference(),
                    items.stream().map(ItemResponse::from).toList(), c.getCreatedAt());
        }
    }
}
