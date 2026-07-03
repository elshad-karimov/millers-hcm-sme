package az.millers.hcm.compbenefits.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.compbenefits.domain.OpenEnrollmentWindow;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** DTOs for open-enrollment windows (HCM_11 M379). */
public final class OpenEnrollmentDtos {

    private OpenEnrollmentDtos() {}

    public record WindowRequest(
            @NotNull Integer planYear,
            @NotBlank String name,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            String notes,
            Boolean active) {
    }

    public record WindowResponse(
            UUID id,
            int planYear,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            boolean active,
            boolean openNow,
            String notes,
            OffsetDateTime createdAt) {

        public static WindowResponse from(OpenEnrollmentWindow w, LocalDate today) {
            return new WindowResponse(
                    w.getId(), w.getPlanYear(), w.getName(), w.getStartDate(), w.getEndDate(),
                    w.isActive(), w.isOpenOn(today), w.getNotes(), w.getCreatedAt());
        }
    }

    /** "Is open enrollment currently running?" summary. */
    public record OpenStatus(
            boolean open,
            UUID windowId,
            String windowName,
            Integer planYear,
            LocalDate startDate,
            LocalDate endDate) {

        public static OpenStatus closed() {
            return new OpenStatus(false, null, null, null, null, null);
        }

        public static OpenStatus of(OpenEnrollmentWindow w) {
            return new OpenStatus(true, w.getId(), w.getName(), w.getPlanYear(),
                    w.getStartDate(), w.getEndDate());
        }
    }
}
