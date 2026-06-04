package az.millers.hcm.performance.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.performance.api.dto.SuccessionGridDtos.Readiness;

/** DTOs for the M103 named-successor nomination surface. */
public final class SuccessionNominationDtos {
    private SuccessionNominationDtos() {}

    public record NominationRequest(
            UUID nomineeEmployeeId,
            Readiness readinessTier,
            String notes) {}

    public record CancelRequest(String reason) {}

    public record NominationResponse(
            UUID id,
            UUID positionId,
            String positionCode,
            String positionTitle,
            UUID nomineeEmployeeId,
            String nomineeName,
            String nomineeEmployeeNo,
            String department,
            Readiness readinessTier,
            String notes,
            String nominatedBy,
            OffsetDateTime createdAt,
            boolean active) {}
}
