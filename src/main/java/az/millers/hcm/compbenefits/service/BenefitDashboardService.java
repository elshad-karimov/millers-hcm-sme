package az.millers.hcm.compbenefits.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.compbenefits.domain.BenefitClaim;
import az.millers.hcm.compbenefits.domain.BenefitClaimStatus;
import az.millers.hcm.compbenefits.domain.BenefitEnrollment;
import az.millers.hcm.compbenefits.domain.BenefitPlan;
import az.millers.hcm.compbenefits.domain.EnrollmentStatus;
import az.millers.hcm.compbenefits.repo.BenefitClaimRepository;
import az.millers.hcm.compbenefits.repo.BenefitEnrollmentRepository;
import az.millers.hcm.compbenefits.repo.BenefitLifeEventRepository;
import az.millers.hcm.compbenefits.repo.BenefitPlanRepository;

/**
 * HCM_11 M386 — read-only benefits dashboard: enrolment/plan stats, employer spend,
 * enrolment-by-status, claims summary, pending approvals, open life-event windows, and
 * expiring plans. Benefits-admin facing (READ_BENEFITS).
 */
@Service
public class BenefitDashboardService {

    private static final String TENANT = "default";

    private final BenefitPlanRepository plans;
    private final BenefitEnrollmentRepository enrollments;
    private final BenefitClaimRepository claims;
    private final BenefitLifeEventRepository lifeEvents;
    private final BenefitCostService costs;

    public BenefitDashboardService(BenefitPlanRepository plans,
                                   BenefitEnrollmentRepository enrollments,
                                   BenefitClaimRepository claims,
                                   BenefitLifeEventRepository lifeEvents,
                                   BenefitCostService costs) {
        this.plans = plans;
        this.enrollments = enrollments;
        this.claims = claims;
        this.lifeEvents = lifeEvents;
        this.costs = costs;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> overview() {
        List<BenefitPlan> allPlans = plans.findByTenantId(TENANT);
        long activePlans = allPlans.stream().filter(BenefitPlan::isActive).count();

        List<BenefitEnrollment> allEnr = enrollments.findByTenantId(TENANT);
        Map<String, Long> byStatus = allEnr.stream()
                .collect(Collectors.groupingBy(e -> e.getStatus().name(),
                        LinkedHashMap::new, Collectors.counting()));
        long active = byStatus.getOrDefault(EnrollmentStatus.ENROLLED.name(), 0L);
        long pending = byStatus.getOrDefault(EnrollmentStatus.PENDING_APPROVAL.name(), 0L);

        BigDecimal employerMonthly = costs.currentEmployerMonthlySpend();
        BigDecimal employeeMonthly = enrollments
                .findByTenantIdAndStatusOrderByStartDateDesc(TENANT, EnrollmentStatus.ENROLLED).stream()
                .map(e -> e.getEmployeeContribution() == null ? BigDecimal.ZERO : e.getEmployeeContribution())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Claims summary
        List<BenefitClaim> allClaims = claims.findByTenantIdOrderByClaimDateDesc(TENANT);
        Map<String, Long> claimsByStatus = allClaims.stream()
                .collect(Collectors.groupingBy(c -> c.getStatus().name(),
                        LinkedHashMap::new, Collectors.counting()));
        BigDecimal paidTotal = allClaims.stream()
                .filter(c -> c.getStatus() == BenefitClaimStatus.PAID)
                .map(c -> c.getApprovedAmount() == null ? c.getTotalAmount() : c.getApprovedAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Open life-event special windows
        LocalDate today = LocalDate.now();
        long openLifeWindows = lifeEvents.findByTenantIdOrderByEventDateDesc(TENANT).stream()
                .filter(e -> e.isWindowOpenOn(today)).count();

        // Plans expiring within 60 days
        LocalDate soon = today.plusDays(60);
        long expiringPlans = allPlans.stream()
                .filter(p -> p.isActive() && p.getEffectiveTo() != null
                        && !p.getEffectiveTo().isBefore(today) && !p.getEffectiveTo().isAfter(soon))
                .count();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalPlans", allPlans.size());
        out.put("activePlans", activePlans);
        out.put("totalEnrollments", allEnr.size());
        out.put("activeEnrollments", active);
        out.put("pendingApprovals", pending);
        out.put("employerMonthly", employerMonthly);
        out.put("employeeMonthly", employeeMonthly);
        out.put("employerAnnual", employerMonthly.multiply(BigDecimal.valueOf(12)));
        out.put("enrollmentsByStatus", byStatus);
        out.put("byCategory", costs.employerSpendByCategory());
        out.put("claimsByStatus", claimsByStatus);
        out.put("claimsPaidTotal", paidTotal);
        out.put("openLifeEventWindows", openLifeWindows);
        out.put("expiringPlans", expiringPlans);
        return out;
    }
}
