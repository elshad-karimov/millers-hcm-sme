package az.millers.hcm.staffing.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import az.millers.hcm.staffing.domain.ReasonCategory;
import az.millers.hcm.staffing.domain.ReasonMaster;

/** M259 — Reason master DTOs (PRD §22). */
public final class ReasonMasterDtos {

    private ReasonMasterDtos() {}

    public record ReasonResponse(
            UUID id,
            ReasonCategory category,
            String code,
            String label,
            String description,
            boolean active,
            short sortOrder) {

        public static ReasonResponse from(ReasonMaster r) {
            return new ReasonResponse(
                    r.getId(), r.getCategory(), r.getCode(),
                    r.getLabel(), r.getDescription(),
                    r.isActive(), r.getSortOrder());
        }
    }

    public record ReasonRequest(
            @NotNull ReasonCategory category,
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 200) String label,
            @Size(max = 500) String description,
            Boolean active,
            Short sortOrder) {}
}
