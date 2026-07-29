package az.millers.hcm.compbenefits.service;
import az.millers.hcm.common.tenant.TenantContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.compbenefits.domain.BenefitCategory;
import az.millers.hcm.compbenefits.domain.BenefitEnrollment;
import az.millers.hcm.compbenefits.domain.BenefitPlan;
import az.millers.hcm.compbenefits.domain.EnrollmentStatus;
import az.millers.hcm.compbenefits.repo.BenefitCategoryRepository;
import az.millers.hcm.compbenefits.repo.BenefitEnrollmentRepository;
import az.millers.hcm.compbenefits.repo.BenefitPlanRepository;

/**
 * HCM_11 M383 — benefit cost calculations.
 *
 * <ul>
 *   <li>{@link #annualEmployerCost(UUID, int)} fills the compensation Total-Comp statement's
 *       employer_benefits_total (was a ZERO placeholder).</li>
 *   <li>{@link #employerSpendByCategory()} + {@link #currentEmployerMonthlySpend()} feed the
 *       benefits dashboard / finance (M386).</li>
 * </ul>
 *
 * <p>Full GL-journal-line posting of employer benefit cost is a documented later seam
 * (needs gl_account_mapping config + per-run vs per-month reconciliation).
 */
@Service
public class BenefitCostService {


    private final BenefitEnrollmentRepository enrollments;
    private final BenefitPlanRepository plans;
    private final BenefitCategoryRepository categories;

    public BenefitCostService(BenefitEnrollmentRepository enrollments,
                              BenefitPlanRepository plans,
                              BenefitCategoryRepository categories) {
        this.enrollments = enrollments;
        this.plans = plans;
        this.categories = categories;
    }

    private static boolean countsForCost(EnrollmentStatus s) {
        // Coverage that actually incurred employer cost during a period.
        return s == EnrollmentStatus.ENROLLED
                || s == EnrollmentStatus.SUSPENDED
                || s == EnrollmentStatus.TERMINATED;
    }

    /** Annual employer benefit cost for an employee — sum over enrollments of monthly employer
     *  contribution × months the enrollment overlapped the given year. */
    @Transactional(readOnly = true)
    public BigDecimal annualEmployerCost(UUID employeeId, int year) {
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        BigDecimal total = BigDecimal.ZERO;
        for (BenefitEnrollment e : enrollments.findByEmployeeIdOrderByStartDateDesc(employeeId)) {
            if (!countsForCost(e.getStatus())) continue;
            if (e.getStartDate() == null) continue;
            LocalDate from = e.getStartDate().isAfter(yearStart) ? e.getStartDate() : yearStart;
            LocalDate to = (e.getEndDate() == null || e.getEndDate().isAfter(yearEnd)) ? yearEnd : e.getEndDate();
            if (to.isBefore(from)) continue;
            long months = ChronoUnit.MONTHS.between(from.withDayOfMonth(1), to.withDayOfMonth(1)) + 1;
            BigDecimal er = e.getEmployerContribution() == null ? BigDecimal.ZERO : e.getEmployerContribution();
            total = total.add(er.multiply(BigDecimal.valueOf(months)));
        }
        return total;
    }

    /** Current monthly employer spend across all ENROLLED enrollments (tenant-wide). */
    @Transactional(readOnly = true)
    public BigDecimal currentEmployerMonthlySpend() {
        return enrollments.findByTenantIdAndStatusOrderByStartDateDesc(TenantContext.current(), EnrollmentStatus.ENROLLED).stream()
                .map(e -> e.getEmployerContribution() == null ? BigDecimal.ZERO : e.getEmployerContribution())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Current monthly employer + employee spend broken down by benefit category. */
    @Transactional(readOnly = true)
    public List<CategorySpend> employerSpendByCategory() {
        Map<UUID, BenefitPlan> planCache = new java.util.HashMap<>();
        Map<UUID, String> catNames = new java.util.HashMap<>();
        Map<String, CategorySpend> byCat = new LinkedHashMap<>();
        for (BenefitEnrollment e : enrollments.findByTenantIdAndStatusOrderByStartDateDesc(TenantContext.current(), EnrollmentStatus.ENROLLED)) {
            BenefitPlan plan = planCache.computeIfAbsent(e.getPlanId(),
                    id -> plans.findById(id).orElse(null));
            String catName = "Uncategorised";
            if (plan != null && plan.getCategoryId() != null) {
                catName = catNames.computeIfAbsent(plan.getCategoryId(), id ->
                        categories.findById(id).map(BenefitCategory::getName).orElse("Uncategorised"));
            }
            CategorySpend cs = byCat.computeIfAbsent(catName,
                    k -> new CategorySpend(k, 0, BigDecimal.ZERO, BigDecimal.ZERO));
            BigDecimal er = e.getEmployerContribution() == null ? BigDecimal.ZERO : e.getEmployerContribution();
            BigDecimal ee = e.getEmployeeContribution() == null ? BigDecimal.ZERO : e.getEmployeeContribution();
            byCat.put(catName, new CategorySpend(catName, cs.enrollments() + 1,
                    cs.employerMonthly().add(er), cs.employeeMonthly().add(ee)));
        }
        return List.copyOf(byCat.values());
    }

    /** Monthly employer/employee spend for one benefit category. */
    public record CategorySpend(String category, long enrollments,
                                BigDecimal employerMonthly, BigDecimal employeeMonthly) {}
}
