package az.millers.hcm.corehr.api.dto;

import az.millers.hcm.corehr.domain.EmploymentStatus;
import jakarta.validation.constraints.NotNull;

public record StatusChangeRequest(
        @NotNull EmploymentStatus newStatus,
        String reason) {
}
