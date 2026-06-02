package az.millers.hcm.corehr.api.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Size;

public record HealthRequest(
        LocalDate fitnessCertificateDate,
        LocalDate nextExamDate,
        @Size(max = 4000) String occupationalHealthNotes,
        @Size(max = 4000) String restrictions,
        Boolean confidential) {
}
