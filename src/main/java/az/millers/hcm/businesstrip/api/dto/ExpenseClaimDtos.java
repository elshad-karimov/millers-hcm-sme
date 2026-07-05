package az.millers.hcm.businesstrip.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import az.millers.hcm.businesstrip.domain.ClaimStatus;
import az.millers.hcm.businesstrip.domain.ExpenseCategory;

/** DTOs for M104 expense claims. */
public final class ExpenseClaimDtos {
    private ExpenseClaimDtos() {}

    public record ItemRequest(
            ExpenseCategory category,
            String description,
            BigDecimal amount,
            LocalDate itemDate,
            String receiptUrl) {}

    public record CreateClaimRequest(
            UUID tripId,
            String currency,
            String notes,
            List<ItemRequest> items) {}

    public record ItemResponse(
            UUID id,
            ExpenseCategory category,
            String description,
            BigDecimal amount,
            LocalDate itemDate,
            String receiptUrl,
            String policyFlags) {}

    public record ClaimResponse(
            UUID id,
            String claimNo,
            UUID tripId,
            String tripNo,
            String destination,
            UUID employeeId,
            String employeeName,
            String currency,
            ClaimStatus status,
            BigDecimal totalAmount,
            OffsetDateTime submittedAt,
            OffsetDateTime approvedAt,
            String approvedBy,
            OffsetDateTime rejectedAt,
            String rejectionReason,
            OffsetDateTime paidAt,
            String notes,
            OffsetDateTime createdAt,
            List<ItemResponse> items) {}

    public record RejectRequest(String reason) {}
}
