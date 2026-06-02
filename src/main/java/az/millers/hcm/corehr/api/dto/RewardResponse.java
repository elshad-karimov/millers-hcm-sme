package az.millers.hcm.corehr.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.corehr.domain.EmployeeReward;
import az.millers.hcm.corehr.domain.RewardType;

public record RewardResponse(
        UUID id,
        UUID employeeId,
        RewardType rewardType,
        String title,
        String description,
        BigDecimal awardValue,
        String currency,
        String awardedBy,
        LocalDate awardedAt,
        String certificateUrl,
        String notes,
        OffsetDateTime createdAt,
        String createdBy,
        OffsetDateTime updatedAt,
        String updatedBy) {

    public static RewardResponse from(EmployeeReward r) {
        return new RewardResponse(
                r.getId(),
                r.getEmployeeId(),
                r.getRewardType(),
                r.getTitle(),
                r.getDescription(),
                r.getAwardValue(),
                r.getCurrency(),
                r.getAwardedBy(),
                r.getAwardedAt(),
                r.getCertificateUrl(),
                r.getNotes(),
                r.getCreatedAt(),
                r.getCreatedBy(),
                r.getUpdatedAt(),
                r.getUpdatedBy());
    }
}
