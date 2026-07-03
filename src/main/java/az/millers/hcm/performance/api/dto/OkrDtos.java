package az.millers.hcm.performance.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import az.millers.hcm.performance.domain.OkrCheckIn;
import az.millers.hcm.performance.domain.OkrKeyResult;
import az.millers.hcm.performance.domain.OkrObjective;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** DTOs for OKR objectives / key results / check-ins (HCM_12 M391). */
public final class OkrDtos {

    private OkrDtos() {}

    public record ObjectiveRequest(
            @NotBlank @Size(max = 300) String title,
            String description,
            @NotBlank String okrLevel,
            UUID parentId,
            UUID ownerEmployeeId,
            UUID orgUnitId,
            UUID legalEntityId,
            UUID cycleId,
            LocalDate periodStart,
            LocalDate periodEnd,
            LocalDate dueDate,
            String confidence) {
    }

    public record KeyResultRequest(
            @NotBlank @Size(max = 300) String title,
            String measurementType,
            BigDecimal baselineValue,
            @NotNull BigDecimal targetValue,
            BigDecimal weightPercent,
            String confidence,
            UUID ownerEmployeeId,
            LocalDate dueDate) {
    }

    public record CheckInRequest(
            UUID keyResultId,
            BigDecimal currentValue,
            String confidence,
            String status,
            @Size(max = 1000) String comment) {
    }

    public record KeyResultResponse(
            UUID id, String title, String measurementType, BigDecimal baselineValue,
            BigDecimal targetValue, BigDecimal currentValue, BigDecimal progressPercent,
            BigDecimal weightPercent, String confidence, UUID ownerEmployeeId,
            LocalDate dueDate, String status) {

        public static KeyResultResponse from(OkrKeyResult kr) {
            return new KeyResultResponse(kr.getId(), kr.getTitle(), kr.getMeasurementType(),
                    kr.getBaselineValue(), kr.getTargetValue(), kr.getCurrentValue(),
                    kr.getProgressPercent(), kr.getWeightPercent(), kr.getConfidence(),
                    kr.getOwnerEmployeeId(), kr.getDueDate(), kr.getStatus());
        }
    }

    public record ObjectiveResponse(
            UUID id, String title, String description, String okrLevel, UUID parentId,
            String parentTitle, UUID ownerEmployeeId, String ownerName, UUID orgUnitId,
            UUID legalEntityId, UUID cycleId, LocalDate periodStart, LocalDate periodEnd,
            LocalDate dueDate, String status, BigDecimal progressPercent, String confidence,
            List<KeyResultResponse> keyResults, OffsetDateTime createdAt) {

        public static ObjectiveResponse from(OkrObjective o, String parentTitle, String ownerName,
                                             List<OkrKeyResult> krs) {
            return new ObjectiveResponse(o.getId(), o.getTitle(), o.getDescription(),
                    o.getOkrLevel(), o.getParentId(), parentTitle, o.getOwnerEmployeeId(),
                    ownerName, o.getOrgUnitId(), o.getLegalEntityId(), o.getCycleId(),
                    o.getPeriodStart(), o.getPeriodEnd(), o.getDueDate(), o.getStatus(),
                    o.getProgressPercent(), o.getConfidence(),
                    krs == null ? List.of() : krs.stream().map(KeyResultResponse::from).toList(),
                    o.getCreatedAt());
        }
    }

    public record CheckInResponse(
            UUID id, UUID keyResultId, BigDecimal oldValue, BigDecimal newValue,
            String confidence, String comment, String recordedBy, OffsetDateTime recordedAt) {

        public static CheckInResponse from(OkrCheckIn c) {
            return new CheckInResponse(c.getId(), c.getKeyResultId(), c.getOldValue(),
                    c.getNewValue(), c.getConfidence(), c.getComment(), c.getRecordedBy(),
                    c.getRecordedAt());
        }
    }
}
