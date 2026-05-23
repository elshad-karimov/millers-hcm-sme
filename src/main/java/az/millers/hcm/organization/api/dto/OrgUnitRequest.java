package az.millers.hcm.organization.api.dto;

import java.util.UUID;

import az.millers.hcm.organization.domain.OrgUnitType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OrgUnitRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 200) String name,
        @NotNull OrgUnitType unitType,
        UUID parentId,
        UUID headEmployeeId,
        Integer sortOrder) {
}
