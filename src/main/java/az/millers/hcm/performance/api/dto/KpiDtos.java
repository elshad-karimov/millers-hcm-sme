package az.millers.hcm.performance.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.performance.domain.Kpi;
import az.millers.hcm.performance.domain.KpiAssignment;
import az.millers.hcm.performance.domain.KpiResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** DTOs for the KPI library + assignments (HCM_12 M390). */
public final class KpiDtos {

    private KpiDtos() {}

    public record KpiRequest(
            @NotBlank @Size(max = 40) String kpiCode,
            @NotBlank @Size(max = 200) String kpiName,
            String category,
            String description,
            String measurementUnit,
            @PositiveOrZero BigDecimal defaultTarget,
            BigDecimal minThreshold,
            BigDecimal maxThreshold,
            String frequency,
            String scoringModel,
            String dataSource,
            Boolean active) {
    }

    public record KpiResponse(
            UUID id, String kpiCode, String kpiName, String category, String description,
            String measurementUnit, BigDecimal defaultTarget, BigDecimal minThreshold,
            BigDecimal maxThreshold, String frequency, String scoringModel, String dataSource,
            boolean active, OffsetDateTime createdAt) {

        public static KpiResponse from(Kpi k) {
            return new KpiResponse(k.getId(), k.getKpiCode(), k.getKpiName(), k.getCategory(),
                    k.getDescription(), k.getMeasurementUnit(), k.getDefaultTarget(),
                    k.getMinThreshold(), k.getMaxThreshold(), k.getFrequency(), k.getScoringModel(),
                    k.getDataSource(), k.isActive(), k.getCreatedAt());
        }
    }

    public record AssignRequest(
            @NotNull UUID kpiId,
            @NotNull UUID cycleId,
            @NotNull UUID employeeId,
            @NotNull @Positive BigDecimal assignedTarget,
            @PositiveOrZero BigDecimal weightPercent) {
    }

    public record MeasureRequest(
            @NotNull BigDecimal actualValue,
            String periodLabel,
            String note) {
    }

    public record AssignmentResponse(
            UUID id, UUID kpiId, String kpiCode, String kpiName, String measurementUnit,
            String scoringModel, UUID cycleId, UUID employeeId, String employeeName,
            BigDecimal assignedTarget, BigDecimal weightPercent, BigDecimal actualValue,
            BigDecimal achievementPercent, BigDecimal rating, String status,
            OffsetDateTime createdAt) {

        public static AssignmentResponse from(KpiAssignment a, Kpi k, String employeeName) {
            return new AssignmentResponse(a.getId(), a.getKpiId(),
                    k == null ? null : k.getKpiCode(), k == null ? null : k.getKpiName(),
                    k == null ? null : k.getMeasurementUnit(), k == null ? null : k.getScoringModel(),
                    a.getCycleId(), a.getEmployeeId(), employeeName, a.getAssignedTarget(),
                    a.getWeightPercent(), a.getActualValue(), a.getAchievementPercent(),
                    a.getRating(), a.getStatus(), a.getCreatedAt());
        }
    }

    public record ResultResponse(
            UUID id, String periodLabel, BigDecimal actualValue, BigDecimal achievementPercent,
            BigDecimal rating, String note, String recordedBy, OffsetDateTime recordedAt) {

        public static ResultResponse from(KpiResult r) {
            return new ResultResponse(r.getId(), r.getPeriodLabel(), r.getActualValue(),
                    r.getAchievementPercent(), r.getRating(), r.getNote(), r.getRecordedBy(),
                    r.getRecordedAt());
        }
    }
}
