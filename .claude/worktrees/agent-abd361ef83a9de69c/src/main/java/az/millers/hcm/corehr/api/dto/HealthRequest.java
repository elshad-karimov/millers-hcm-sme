package az.millers.hcm.corehr.api.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record HealthRequest(
        LocalDate fitnessCertificateDate,
        LocalDate nextExamDate,
        @Size(max = 4000) String occupationalHealthNotes,
        @Size(max = 4000) String restrictions,
        Boolean confidential,
        // ── M137 — Section 18 disability ─────────────────────────────
        @Size(max = 40) String disabilityStatus,
        @Min(0) @Max(100) Integer disabilityPercent,
        @Size(max = 4000) String disabilityNote,
        @Size(max = 4000) String accommodationsNote) {
}
