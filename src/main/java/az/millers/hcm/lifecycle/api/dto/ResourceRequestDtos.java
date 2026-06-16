package az.millers.hcm.lifecycle.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.corehr.domain.AssetType;

/**
 * DTOs for onboarding resource (provisioning) requests (M301 — Phase B.1).
 */
public final class ResourceRequestDtos {
    private ResourceRequestDtos() {}

    public record ResourceRequestResponse(
            UUID id,
            String requestNo,
            UUID employeeId,
            String employeeName,
            UUID assignmentId,
            UUID taskStatusId,
            String category,
            String title,
            String details,
            String status,
            UUID assetId,
            OffsetDateTime requestedAt,
            OffsetDateTime fulfilledAt) {}

    /** Move a request between REQUESTED / IN_PROGRESS / CANCELLED. */
    public record UpdateStatusRequest(
            String status,
            String details) {}

    /**
     * Fulfil a request. When {@code createAsset} is true (equipment/workspace),
     * an {@code EmployeeAsset} is created for the hire and linked; either way
     * the request is marked FULFILLED and the originating checklist task DONE.
     */
    public record FulfillRequest(
            boolean createAsset,
            AssetType assetType,
            String assetName,
            String assetIdentifier,
            LocalDate assignedAt,
            String notes) {}
}
