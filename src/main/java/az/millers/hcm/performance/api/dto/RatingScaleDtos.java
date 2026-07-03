package az.millers.hcm.performance.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import az.millers.hcm.performance.domain.RatingScale;
import az.millers.hcm.performance.domain.RatingScaleType;
import az.millers.hcm.performance.domain.RatingScaleValue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** DTOs for the rating scale master (HCM_12 M388). */
public final class RatingScaleDtos {

    private RatingScaleDtos() {}

    public record ScaleValueRequest(
            @NotNull BigDecimal ratingValue,
            @NotBlank @Size(max = 120) String ratingLabel,
            String description,
            BigDecimal minPercentage,
            BigDecimal maxPercentage,
            String colorCode) {
    }

    public record ScaleRequest(
            @NotBlank @Size(max = 40) String scaleCode,
            @NotBlank @Size(max = 160) String scaleName,
            @NotNull RatingScaleType scaleType,
            String description,
            Boolean active,
            Boolean isDefault,
            @NotNull List<ScaleValueRequest> values) {
    }

    public record ScaleValueResponse(
            UUID id,
            int valueOrder,
            BigDecimal ratingValue,
            String ratingLabel,
            String description,
            BigDecimal minPercentage,
            BigDecimal maxPercentage,
            String colorCode) {

        public static ScaleValueResponse from(RatingScaleValue v) {
            return new ScaleValueResponse(v.getId(), v.getValueOrder(), v.getRatingValue(),
                    v.getRatingLabel(), v.getDescription(), v.getMinPercentage(),
                    v.getMaxPercentage(), v.getColorCode());
        }
    }

    public record ScaleResponse(
            UUID id,
            String scaleCode,
            String scaleName,
            RatingScaleType scaleType,
            String description,
            boolean active,
            boolean isDefault,
            List<ScaleValueResponse> values,
            OffsetDateTime createdAt) {

        public static ScaleResponse from(RatingScale s, List<RatingScaleValue> values) {
            return new ScaleResponse(s.getId(), s.getScaleCode(), s.getScaleName(), s.getScaleType(),
                    s.getDescription(), s.isActive(), s.isDefault(),
                    values.stream().map(ScaleValueResponse::from).toList(), s.getCreatedAt());
        }
    }
}
