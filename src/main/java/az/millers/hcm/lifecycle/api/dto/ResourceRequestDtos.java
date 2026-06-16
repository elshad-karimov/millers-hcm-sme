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
            /** M302 — IT sub-kind + what was provisioned (request-tracking). */
            String provisioningKind,
            String provisionedRef,
            OffsetDateTime requestedAt,
            OffsetDateTime fulfilledAt) {}

    /** Move a request between REQUESTED / IN_PROGRESS / CANCELLED; optionally classify (M302). */
    public record UpdateStatusRequest(
            String status,
            String details,
            String provisioningKind) {}

    /**
     * Fulfil a request. When {@code createAsset} is true (equipment/workspace/
     * access card), an {@code EmployeeAsset} is created for the hire and linked.
     * For IT requests, {@code provisioningKind} + {@code provisionedRef} record
     * what was set up (request-tracking only). Either way the request is marked
     * FULFILLED and the originating checklist task DONE.
     */
    public record FulfillRequest(
            boolean createAsset,
            AssetType assetType,
            String assetName,
            String assetIdentifier,
            LocalDate assignedAt,
            String provisioningKind,
            String provisionedRef,
            String notes) {}
}
