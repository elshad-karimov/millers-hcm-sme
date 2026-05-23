package az.millers.hcm.compbenefits.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.compbenefits.domain.AllowanceCategory;
import az.millers.hcm.compbenefits.domain.AllowanceType;

public record AllowanceTypeResponse(
        UUID id,
        String code,
        String name,
        String description,
        AllowanceCategory category,
        boolean taxable,
        boolean recurring,
        BigDecimal defaultAmount,
        String currency,
        boolean active,
        OffsetDateTime createdAt) {

    public static AllowanceTypeResponse from(AllowanceType t) {
        return new AllowanceTypeResponse(
                t.getId(), t.getCode(), t.getName(), t.getDescription(),
                t.getCategory(), t.isTaxable(), t.isRecurring(),
                t.getDefaultAmount(), t.getCurrency(), t.isActive(), t.getCreatedAt());
    }
}
