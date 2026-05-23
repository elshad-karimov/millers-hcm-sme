package az.millers.hcm.attendance.api.dto;

import az.millers.hcm.attendance.domain.SummaryStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record SummaryCorrectionRequest(
        @NotBlank String reason,
        @Min(0) Integer workedMinutes,
        @Min(0) Integer lateMinutes,
        @Min(0) Integer earlyMinutes,
        @Min(0) Integer breakMinutes,
        @Min(0) Integer overtimeMinutes,
        SummaryStatus status) {
}
