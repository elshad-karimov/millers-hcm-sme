package az.millers.hcm.compbenefits.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.compbenefits.api.dto.BenefitDtos.PlanRequest;
import az.millers.hcm.compbenefits.domain.BenefitPlan;
import az.millers.hcm.compbenefits.domain.BenefitType;

/**
 * Pins benefits-plan validation rules and the employer-spend roll-up math
 * (M108). The DB-touching paths are covered by Spring's integration suite;
 * this file focuses on the pure-static helpers so the math regression
 * surface stays small.
 */
class BenefitsServiceTest {

    // ── validatePlanRequest() ───────────────────────────────────────────────

    @Test
    void validatePlanAcceptsCanonicalRequest() {
        PlanRequest req = req(
                BigDecimal.valueOf(100), BigDecimal.valueOf(50),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        BenefitsService.validatePlanRequest(req);
        // (no exception)
    }

    @Test
    void validatePlanRejectsNegativeEmployerContribution() {
        PlanRequest req = req(
                BigDecimal.valueOf(-1), BigDecimal.valueOf(50),
                LocalDate.of(2026, 1, 1), null);
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> BenefitsService.validatePlanRequest(req))
                .withMessageContaining("employerContribution");
    }

    @Test
    void validatePlanRejectsNegativeEmployeeContribution() {
        PlanRequest req = req(
                BigDecimal.valueOf(100), BigDecimal.valueOf(-1),
                LocalDate.of(2026, 1, 1), null);
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> BenefitsService.validatePlanRequest(req))
                .withMessageContaining("employeeContribution");
    }

    @Test
    void validatePlanRejectsEffectiveToBeforeFrom() {
        PlanRequest req = req(
                BigDecimal.ZERO, BigDecimal.ZERO,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 5, 31));
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> BenefitsService.validatePlanRequest(req))
                .withMessageContaining("effectiveTo");
    }

    @Test
    void validatePlanAcceptsOpenEndedWindow() {
        PlanRequest req = req(
                BigDecimal.ZERO, BigDecimal.ZERO,
                LocalDate.of(2026, 1, 1), null);
        BenefitsService.validatePlanRequest(req);
    }

    @Test
    void validatePlanAcceptsSameDayWindow() {
        LocalDate d = LocalDate.of(2026, 6, 1);
        PlanRequest req = req(BigDecimal.ZERO, BigDecimal.ZERO, d, d);
        BenefitsService.validatePlanRequest(req);
    }

    @Test
    void validatePlanRejectsNullEffectiveFrom() {
        PlanRequest req = req(BigDecimal.ZERO, BigDecimal.ZERO, null, null);
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> BenefitsService.validatePlanRequest(req))
                .withMessageContaining("effectiveFrom");
    }

    // ── totalEmployerSpend() ────────────────────────────────────────────────

    @Test
    void totalSpendZeroWhenNoEnrolments() {
        BenefitPlan plan = plan(BigDecimal.valueOf(200));
        assertThat(BenefitsService.totalEmployerSpend(List.of(plan), Map.of()))
                .isEqualByComparingTo("0");
    }

    @Test
    void totalSpendMultipliesPerEnrolment() {
        BenefitPlan plan = plan(BigDecimal.valueOf(200));
        Map<UUID, Long> counts = Map.of(plan.getId(), 5L);
        assertThat(BenefitsService.totalEmployerSpend(List.of(plan), counts))
                .isEqualByComparingTo("1000"); // 200 × 5
    }

    @Test
    void totalSpendSumsAcrossPlans() {
        BenefitPlan health = plan(BigDecimal.valueOf(300));
        BenefitPlan dental = plan(BigDecimal.valueOf(50));
        Map<UUID, Long> counts = Map.of(
                health.getId(), 10L,
                dental.getId(), 4L);
        // 300×10 + 50×4 = 3000 + 200 = 3200
        assertThat(BenefitsService.totalEmployerSpend(List.of(health, dental), counts))
                .isEqualByComparingTo("3200");
    }

    @Test
    void totalSpendIgnoresMissingCounts() {
        BenefitPlan plan = plan(BigDecimal.valueOf(200));
        // counts map empty for this plan → treated as zero
        assertThat(BenefitsService.totalEmployerSpend(List.of(plan), Map.of(UUID.randomUUID(), 5L)))
                .isEqualByComparingTo("0");
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static PlanRequest req(BigDecimal employer, BigDecimal employee,
                                    LocalDate from, LocalDate to) {
        return new PlanRequest(
                "TEST", "Test plan", null, BenefitType.HEALTH,
                "Acme", null, null,
                employer, employee, "AZN",
                from, to, true);
    }

    private static BenefitPlan plan(BigDecimal employerContribution) {
        BenefitPlan p = new BenefitPlan();
        p.setId(UUID.randomUUID());
        p.setEmployerContribution(employerContribution);
        p.setEmployeeContribution(BigDecimal.ZERO);
        return p;
    }
}
