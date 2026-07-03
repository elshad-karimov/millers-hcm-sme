package az.millers.hcm.compbenefits.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.compbenefits.domain.BenefitCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** DTOs for the benefit category master (HCM_11 M373). */
public final class BenefitCategoryDtos {

    private BenefitCategoryDtos() {}

    /** Create / update payload for a {@link BenefitCategory}. */
    public record CategoryRequest(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 160) String name,
            String description,
            Boolean taxable,
            Boolean requiresProvider,
            Integer displayOrder,
            Boolean active) {
    }

    /** Read view of a {@link BenefitCategory}. */
    public record CategoryResponse(
            UUID id,
            String code,
            String name,
            String description,
            boolean taxable,
            boolean requiresProvider,
            int displayOrder,
            boolean active,
            OffsetDateTime createdAt) {

        public static CategoryResponse from(BenefitCategory c) {
            return new CategoryResponse(
                    c.getId(), c.getCode(), c.getName(), c.getDescription(),
                    c.isTaxable(), c.isRequiresProvider(), c.getDisplayOrder(),
                    c.isActive(), c.getCreatedAt());
        }
    }
}
