package az.millers.hcm.organization.api.dto;

import java.time.OffsetDateTime;

import az.millers.hcm.organization.domain.OrgUnitTypeConfig;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** M143 — wire DTOs for the org-unit type config registry. */
public final class OrgUnitTypeConfigDtos {

    private OrgUnitTypeConfigDtos() {}

    public record OrgUnitTypeConfigRequest(
            /** Upper-case alphanum + underscore, max 64 chars. Immutable after create. */
            @NotBlank @Size(max = 64)
            @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "Code must be upper-case alphanumeric/underscore starting with a letter")
            String code,
            @NotBlank @Size(max = 200) String label,
            @Size(max = 7) String color,
            Integer sortOrder,
            Boolean canHaveChildren,
            Boolean rootLevel,
            /** JSON array of allowed parent type codes, e.g. [\"BRANCH\",\"DIVISION\"]. Null = any. */
            String allowedParentTypes,
            Boolean active,
            String notes) {}

    public record OrgUnitTypeConfigResponse(
            String code,
            String label,
            String color,
            int sortOrder,
            boolean canHaveChildren,
            boolean rootLevel,
            String allowedParentTypes,
            boolean active,
            String notes,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {

        public static OrgUnitTypeConfigResponse from(OrgUnitTypeConfig c) {
            return new OrgUnitTypeConfigResponse(
                    c.getCode(), c.getLabel(), c.getColor(),
                    c.getSortOrder(), c.isCanHaveChildren(), c.isRootLevel(),
                    c.getAllowedParentTypes(), c.isActive(), c.getNotes(),
                    c.getCreatedAt(), c.getUpdatedAt());
        }
    }
}
