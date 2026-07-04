package az.millers.hcm.payroll.api.dto;

import az.millers.hcm.payroll.domain.BankFileFormat;
import az.millers.hcm.payroll.domain.PayCycle;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PayrollGroupRequest(
        @NotBlank @Size(max = 40)
        @Pattern(regexp = "^[A-Z0-9_-]+$", message = "code must be uppercase alphanumeric")
        String code,
        @NotBlank @Size(max = 160) String name,
        @Size(max = 4000) String description,
        PayCycle payCycle,
        BankFileFormat bankFileFormat,
        @Pattern(regexp = "^[A-Z]{3}$", message = "defaultCurrency must be a 3-letter ISO 4217 code")
        String defaultCurrency,
        Object rulesJson,
        Boolean active,
        Boolean defaultGroup) {
}
