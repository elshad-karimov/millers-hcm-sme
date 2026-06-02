package az.millers.hcm.corehr.api.dto;

import java.time.LocalDate;

import az.millers.hcm.corehr.domain.AddressType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotNull AddressType addressType,
        @NotBlank @Size(max = 200) String addressLine1,
        @Size(max = 200) String addressLine2,
        @Size(max = 120) String city,
        @Size(max = 120) String district,
        @Pattern(regexp = "^[A-Z]{2}$", message = "country must be an ISO 3166-1 alpha-2 code")
        String country,
        @Size(max = 20) String postalCode,
        @NotNull LocalDate effectiveFrom) {
}
