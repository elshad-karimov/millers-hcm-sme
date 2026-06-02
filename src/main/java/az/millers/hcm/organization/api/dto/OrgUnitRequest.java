package az.millers.hcm.organization.api.dto;

import java.util.UUID;

import az.millers.hcm.organization.domain.OrgUnitType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OrgUnitRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 200) String name,
        @NotNull OrgUnitType unitType,
        UUID parentId,
        UUID headEmployeeId,
        Integer sortOrder,
        /** M81 — finance / facilities attributes. All optional. */
        @Size(max = 64) String costCentreCode,
        @Size(max = 200) String location,
        @Email @Size(max = 160) String contactEmail,
        @Size(max = 64) String glAccount,
        @Min(value = 0, message = "headcountBudget must be ≥ 0") Integer headcountBudget,
        /** M81 — defaults to true; null on update keeps existing value. */
        Boolean active) {
}
