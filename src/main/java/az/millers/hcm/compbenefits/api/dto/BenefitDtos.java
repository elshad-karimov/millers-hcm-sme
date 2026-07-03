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
            UUID categoryId,
            Integer planYear,
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
            UUID categoryId,
            String categoryName,
            Integer planYear,
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
            return from(p, 0L, null, null);
        }

        public static PlanResponse from(BenefitPlan p, long activeEnrolments) {
            return from(p, activeEnrolments, null, null);
        }

        public static PlanResponse from(BenefitPlan p, long activeEnrolments, String providerName) {
            return from(p, activeEnrolments, providerName, null);
        }

        public static PlanResponse from(BenefitPlan p, long activeEnrolments,
                                        String providerName, String categoryName) {
            return new PlanResponse(
                    p.getId(), p.getCode(), p.getName(), p.getDescription(),
                    p.getBenefitType(), p.getCategoryId(), categoryName, p.getPlanYear(),
                    p.getProvider(), p.getProviderId(), providerName,
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
            az.millers.hcm.compbenefits.domain.BenefitCoverageTier coverageTierCode,
            java.util.List<UUID> dependentIds,
            @PositiveOrZero Integer dependentsCovered,
            String notes) {
    }

    /** End an active enrolment. */
    public record TerminateRequest(
            @NotNull LocalDate endDate,
            String terminationReason) {
    }

    /** A dependent covered by an enrollment. */
    public record CoveredDependent(UUID id, String name) {}

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
            az.millers.hcm.compbenefits.domain.BenefitCoverageTier coverageTierCode,
            Integer planYear,
            LocalDate startDate,
            LocalDate endDate,
            int dependentsCovered,
            java.util.List<CoveredDependent> coveredDependents,
            String notes,
            String enrolledBy,
            OffsetDateTime enrolledAt,
            String terminatedBy,
            OffsetDateTime terminatedAt,
            String terminationReason,
            String currency,
            BigDecimal employerContribution,
            BigDecimal employeeContribution) {

        public static EnrollmentResponse from(BenefitEnrollment e,
                                              BenefitPlan plan,
                                              String employeeName) {
            return from(e, plan, employeeName, java.util.List.of());
        }

        public static EnrollmentResponse from(BenefitEnrollment e,
                                              BenefitPlan plan,
                                              String employeeName,
                                              java.util.List<CoveredDependent> coveredDependents) {
            return new EnrollmentResponse(
                    e.getId(),
                    e.getPlanId(),
                    plan == null ? null : plan.getCode(),
                    plan == null ? null : plan.getName(),
                    plan == null ? null : plan.getBenefitType(),
                    e.getEmployeeId(),
                    employeeName,
                    e.getStatus(),
                    e.getCoverageTierCode(),
                    e.getPlanYear(),
                    e.getStartDate(),
                    e.getEndDate(),
                    e.getDependentsCovered(),
                    coveredDependents,
                    e.getNotes(),
                    e.getEnrolledBy(),
                    e.getEnrolledAt(),
                    e.getTerminatedBy(),
                    e.getTerminatedAt(),
                    e.getTerminationReason(),
                    e.getCurrency(),
                    // Prefer the enrolment snapshot; fall back to the plan for legacy rows.
                    snapshotOr(e.getEmployerContribution(), plan == null ? null : plan.getEmployerContribution()),
                    snapshotOr(e.getEmployeeContribution(), plan == null ? null : plan.getEmployeeContribution()));
        }

        private static BigDecimal snapshotOr(BigDecimal snapshot, BigDecimal planValue) {
            if (snapshot != null && snapshot.signum() != 0) return snapshot;
            return snapshot != null ? snapshot : planValue;
        }
    }
}
