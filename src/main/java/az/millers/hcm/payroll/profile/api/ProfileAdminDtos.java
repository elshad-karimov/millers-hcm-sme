package az.millers.hcm.payroll.profile.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request payloads for configuring the calculation profiles.
 *
 * <p>Every change here moves money, so every one of them requires a reason and
 * is audit logged. The ranges are deliberately generous but bounded: they exist
 * to stop a typo becoming a payslip, not to encode policy.
 */
public final class ProfileAdminDtos {

    private ProfileAdminDtos() {
    }

    /**
     * Answer one or more of the unresolved profile questions.
     *
     * <p>Null means "leave as it is", so answering Q2 does not accidentally
     * assert an answer to Q1.
     */
    public record UpdateProfileSettings(
            /** BLOCKERS Q2 — 3.50 or 2.75 for rotation. */
            @DecimalMin(value = "0.0", message = "An excess multiplier cannot be negative.")
            @DecimalMax(value = "10.0", message = "An excess multiplier above 10x is almost "
                    + "certainly a typo — 3.50 and 2.75 are the values under discussion.")
            BigDecimal excessMultiplier,

            /** BLOCKERS Q1 — are night hours extra hours, or already counted? */
            Boolean nightHoursSeparateFromBase,

            /** BLOCKERS Q6.1 — which categories the balancing accumulator sums. */
            @Size(max = 500)
            String accumulatorCategories,

            /** BLOCKERS Q6 — does derived offshore subtract sick hours? */
            Boolean derivedOffshoreDeductsSick,

            @DecimalMin(value = "0.0")
            @DecimalMax(value = "10.0", message = "An offshore multiplier above 10x is a typo.")
            BigDecimal offshoreMultiplier,

            @NotBlank(message = "A reason is required: this changes what people are paid.")
            @Size(max = 2000)
            String reason) {
    }

    /** Put an employee on a calculation profile, from a date. */
    public record AssignProfile(
            @NotNull UUID employeeId,
            @NotBlank @Size(max = 40) String profileCode,
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo,
            @NotBlank(message = "A reason is required: the profile decides how pay is derived.")
            @Size(max = 2000) String reason) {
    }

    /** One employee's MEWA entitlement. */
    public record UpsertMewaRule(
            @NotNull UUID employeeId,
            @NotNull
            @DecimalMin(value = "0.0", message = "A MEWA rate cannot be negative.")
            @DecimalMax(value = "5.0", message = "A MEWA rate above 500% is a typo — the "
                    + "observed values are 0.30 and 0.60.")
            BigDecimal rate,
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo,
            @NotBlank @Size(max = 2000) String reason) {
    }

    /** Norm working hours for a period — the divisor behind every rate. */
    public record UpsertNormHours(
            @Min(2000) @Max(2100) int year,
            @Min(1) @Max(12) int month,
            @NotNull
            @DecimalMin(value = "0.01", message = "Norm hours must be positive — every rate "
                    + "divides by them.")
            @DecimalMax(value = "744.0", message = "Norm hours cannot exceed the hours in a month.")
            BigDecimal normHours) {
    }

    /** Close a balancing period and record how it was settled. */
    public record SettleExcess(
            @NotNull UUID employeeId,
            @Min(2000) @Max(2100) int periodYear,
            @Min(1) @Max(3) int periodSeq,
            /** The payroll period that will carry the payment. */
            @Min(2000) @Max(2100) int paidInYear,
            @Min(1) @Max(12) int paidInMonth,
            @NotBlank(message = "A reason is required: settling releases money.")
            @Size(max = 2000) String note) {
    }
}
