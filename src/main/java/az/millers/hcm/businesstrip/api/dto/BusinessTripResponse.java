package az.millers.hcm.businesstrip.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.businesstrip.domain.BusinessTripRequest;
import az.millers.hcm.businesstrip.domain.TripStatus;
import az.millers.hcm.businesstrip.domain.TripType;

public record BusinessTripResponse(
        UUID id,
        String tripNo,
        UUID employeeId,
        TripType tripType,
        String destinationCountry,
        String destinationCity,
        String purpose,
        String project,
        String costCentre,
        LocalDate startDate,
        LocalDate endDate,
        int totalDays,
        String currency,
        BigDecimal dailyAllowance,
        BigDecimal requestedAdvance,
        BigDecimal approvedAdvance,
        BigDecimal paidAdvance,
        BigDecimal actualExpense,
        BigDecimal advanceBalance,
        boolean mealsProvided,
        boolean accommodationProvided,
        String attachmentUrls,
        TripStatus status,
        UUID workflowInstanceId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String createdBy) {

    public static BusinessTripResponse from(BusinessTripRequest t) {
        BigDecimal approved = t.getApprovedAdvance() == null ? BigDecimal.ZERO : t.getApprovedAdvance();
        BigDecimal actual = t.getActualExpense() == null ? BigDecimal.ZERO : t.getActualExpense();
        return new BusinessTripResponse(
                t.getId(), t.getTripNo(), t.getEmployeeId(), t.getTripType(),
                t.getDestinationCountry(), t.getDestinationCity(), t.getPurpose(),
                t.getProject(), t.getCostCentre(),
                t.getStartDate(), t.getEndDate(), t.getTotalDays(),
                t.getCurrency(), t.getDailyAllowance(),
                t.getRequestedAdvance(), t.getApprovedAdvance(),
                t.getPaidAdvance(), t.getActualExpense(),
                approved.subtract(actual),
                t.isMealsProvided(), t.isAccommodationProvided(),
                t.getAttachmentUrls(),
                t.getStatus(), t.getWorkflowInstanceId(),
                t.getCreatedAt(), t.getUpdatedAt(), t.getCreatedBy());
    }
}
