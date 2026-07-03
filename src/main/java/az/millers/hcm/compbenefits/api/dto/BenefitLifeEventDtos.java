package az.millers.hcm.compbenefits.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.compbenefits.domain.BenefitLifeEvent;
import az.millers.hcm.compbenefits.domain.BenefitLifeEventType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** DTOs for qualifying life events (HCM_11 M380). */
public final class BenefitLifeEventDtos {

    private BenefitLifeEventDtos() {}

    public record EventRequest(
            @NotNull UUID employeeId,
            @NotNull BenefitLifeEventType eventType,
            @NotNull LocalDate eventDate,
            @Positive Integer windowDays,
            String notes) {
    }

    public record ReviewRequest(String reviewNotes) {}

    public record EventResponse(
            UUID id,
            UUID employeeId,
            String employeeName,
            BenefitLifeEventType eventType,
            LocalDate eventDate,
            int windowDays,
            LocalDate windowEnd,
            boolean windowOpenNow,
            String status,
            String notes,
            String reportedBy,
            String reviewedBy,
            OffsetDateTime reviewedAt,
            String reviewNotes,
            OffsetDateTime createdAt) {

        public static EventResponse from(BenefitLifeEvent e, String employeeName, LocalDate today) {
            return new EventResponse(
                    e.getId(), e.getEmployeeId(), employeeName, e.getEventType(), e.getEventDate(),
                    e.getWindowDays(), e.windowEnd(), e.isWindowOpenOn(today), e.getStatus(),
                    e.getNotes(), e.getReportedBy(), e.getReviewedBy(), e.getReviewedAt(),
                    e.getReviewNotes(), e.getCreatedAt());
        }
    }
}
