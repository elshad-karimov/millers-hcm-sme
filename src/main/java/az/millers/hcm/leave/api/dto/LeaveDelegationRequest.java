package az.millers.hcm.leave.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record LeaveDelegationRequest(
        @NotNull UUID delegateId,
        String delegationScope
) {}
