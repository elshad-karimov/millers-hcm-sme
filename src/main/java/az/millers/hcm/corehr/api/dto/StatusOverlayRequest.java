package az.millers.hcm.corehr.api.dto;

import java.time.LocalDate;
import java.util.UUID;

import az.millers.hcm.corehr.domain.EmployeeStatusOverlay.OverlaySource;
import az.millers.hcm.corehr.domain.EmploymentStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Create / extend a status overlay (M78 / P2-13). The service rejects
 * primary-only statuses (ACTIVE, TERMINATED, etc.) — overlays are for
 * transient states that co-exist with a primary.
 */
public record StatusOverlayRequest(
        @NotNull EmploymentStatus status,
        OverlaySource source,
        UUID sourceId,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo,
        @Size(max = 4000) String notes) {
}
