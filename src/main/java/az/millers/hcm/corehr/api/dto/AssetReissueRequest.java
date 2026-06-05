package az.millers.hcm.corehr.api.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * M124 — atomic close-and-reissue payload. The previous holder is read
 * from the asset row; the SPA only has to identify the new holder, the
 * effective date, and (optionally) the condition snapshots and notes.
 */
public record AssetReissueRequest(
        @NotNull UUID newEmployeeId,
        @NotNull LocalDate effectiveAt,
        @Size(max = 40) String conditionAtReturn,
        @Size(max = 40) String conditionAtAssignment,
        @Size(max = 4000) String notes) {
}
