package az.millers.hcm.compbenefits.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import az.millers.hcm.compbenefits.domain.BenefitEnrollment;
import az.millers.hcm.compbenefits.domain.BenefitPlan;
import az.millers.hcm.compbenefits.domain.BenefitType;
import az.millers.hcm.compbenefits.domain.EnrollmentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** DTOs for Benefits administration (M108). */
public final class BenefitDtos {

    private BenefitDtos() {}

    /** Create / update payload for a {@link BenefitPlan}. */
    public record PlanRequest(
            @NotBlank String code,
            @NotBlank String name,
            String description,
            @NotNull BenefitType benefitType,
            String provider,
            UUID providerId,
            String coverageDetails,
            String eligibility,
            @PositiveOrZero BigDecimal employerContribution,
            @PositiveOrZero BigDecimal employeeContribution,
            String currency,
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo,
            Boolean active) {
    }

    /** Read view of a {@link BenefitPlan}. */
    public record PlanResponse(
            UUID id,
            String code,
            String name,
            String description,
            BenefitType benefitType,
            String provider,
            UUID providerId,
            String providerName,
            String coverageDetails,
            String eligibility,
            BigDecimal employerContribution,
            BigDecimal employeeContribution,
            BigDecimal totalContribution,
            String currency,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            boolean active,
            long activeEnrolments,
            OffsetDateTime createdAt) {

        public static PlanResponse from(BenefitPlan p) {
            return from(p, 0L, null);
        }

        public static PlanResponse from(BenefitPlan p, long activeEnrolments) {
            return from(p, activeEnrolments, null);
        }

        public static PlanResponse from(BenefitPlan p, long activeEnrolments, String providerName) {
            return new PlanResponse(
                    p.getId(), p.getCode(), p.getName(), p.getDescription(),
                    p.getBenefitType(), p.getProvider(), p.getProviderId(), providerName,
                    p.getCoverageDetails(), p.getEligibility(),
                    p.getEmployerContribution(), p.getEmployeeContribution(), p.totalContribution(),
                    p.getCurrency(), p.getEffectiveFrom(), p.getEffectiveTo(),
                    p.isActive(), activeEnrolments, p.getCreatedAt());
        }
    }

    /** Enrol an employee in a plan. */
    public record EnrollmentRequest(
            @NotNull UUID planId,
            @NotNull UUID employeeId,
            @NotNull LocalDate startDate,
            EnrollmentStatus status,
            @PositiveOrZero Integer dependentsCovered,
            String notes) {
    }

    /** End an active enrolment. */
    public record TerminateRequest(
            @NotNull LocalDate endDate,
            String terminationReason) {
    }

    /** Read view of a {@link BenefitEnrollment}. */
    public record EnrollmentResponse(
            UUID id,
            UUID planId,
            String planCode,
            String planName,
            BenefitType benefitType,
            UUID employeeId,
            String employeeName,
            EnrollmentStatus status,
            LocalDate startDate,
            LocalDate endDate,
            int dependentsCovered,
            String notes,
            String enrolledBy,
            OffsetDateTime enrolledAt,
            String terminatedBy,
            OffsetDateTime terminatedAt,
            String terminationReason,
            BigDecimal employerContribution,
            BigDecimal employeeContribution) {

        public static EnrollmentResponse from(BenefitEnrollment e,
                                              BenefitPlan plan,
                                              String employeeName) {
            return new EnrollmentResponse(
                    e.getId(),
                    e.getPlanId(),
                    plan == null ? null : plan.getCode(),
                    plan == null ? null : plan.getName(),
                    plan == null ? null : plan.getBenefitType(),
                    e.getEmployeeId(),
                    employeeName,
                    e.getStatus(),
                    e.getStartDate(),
                    e.getEndDate(),
                    e.getDependentsCovered(),
                    e.getNotes(),
                    e.getEnrolledBy(),
                    e.getEnrolledAt(),
                    e.getTerminatedBy(),
                    e.getTerminatedAt(),
                    e.getTerminationReason(),
                    plan == null ? null : plan.getEmployerContribution(),
                    plan == null ? null : plan.getEmployeeContribution());
        }
    }
}
