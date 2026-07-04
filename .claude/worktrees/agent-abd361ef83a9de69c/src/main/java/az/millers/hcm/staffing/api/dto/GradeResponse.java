package az.millers.hcm.staffing.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.staffing.domain.Grade;

public record GradeResponse(
        UUID id, String code, String name, String description,
        Integer level, BigDecimal minSalary, BigDecimal maxSalary, String currency,
        boolean active,
        OffsetDateTime createdAt, String createdBy,
        OffsetDateTime updatedAt, String updatedBy) {

    public static GradeResponse from(Grade g) {
        return new GradeResponse(
                g.getId(), g.getCode(), g.getName(), g.getDescription(),
                g.getLevel(), g.getMinSalary(), g.getMaxSalary(), g.getCurrency(),
                g.isActive(),
                g.getCreatedAt(), g.getCreatedBy(),
                g.getUpdatedAt(), g.getUpdatedBy());
    }
}
