package az.millers.hcm.organization.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.organization.domain.HrPartner;
import jakarta.validation.constraints.NotNull;

/** M142 — wire DTOs for the HR Partner assignment registry. */
public final class HrPartnerDtos {

    private HrPartnerDtos() {}

    public record HrPartnerRequest(
            @NotNull UUID orgUnitId,
            @NotNull UUID employeeId,
            boolean backup,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            Boolean active,
            String notes) {}

    public record HrPartnerResponse(
            UUID id,
            UUID orgUnitId,
            UUID employeeId,
            boolean backup,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            boolean active,
            String notes,
            OffsetDateTime createdAt,
            String createdBy,
            OffsetDateTime updatedAt,
            String updatedBy) {

        public static HrPartnerResponse from(HrPartner h) {
            return new HrPartnerResponse(
                    h.getId(), h.getOrgUnitId(), h.getEmployeeId(),
                    h.isBackup(), h.getEffectiveFrom(), h.getEffectiveTo(),
                    h.isActive(), h.getNotes(),
                    h.getCreatedAt(), h.getCreatedBy(),
                    h.getUpdatedAt(), h.getUpdatedBy());
        }
    }
}
