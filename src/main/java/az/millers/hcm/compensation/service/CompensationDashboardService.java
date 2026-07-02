package az.millers.hcm.compensation.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.compensation.api.dto.CompensationDashboardDto;
import az.millers.hcm.compensation.api.dto.CompensationDashboardDto.BudgetUtilizationRow;
import az.millers.hcm.compensation.api.dto.OutOfBandReportRow;
import az.millers.hcm.compensation.domain.CompensationBudget;
import az.millers.hcm.compensation.domain.CompensationExceptionStatus;
import az.millers.hcm.compensation.domain.PayBand;
import az.millers.hcm.compensation.domain.SalaryChangeStatus;
import az.millers.hcm.compensation.repo.CompensationBudgetRepository;
import az.millers.hcm.compensation.repo.CompensationExceptionRepository;
import az.millers.hcm.compensation.repo.SalaryChangeRequestRepository;
import az.millers.hcm.compbenefits.api.dto.CompRatioDtos.CompRiskLevel;
import az.millers.hcm.compbenefits.service.CompRatioService;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.domain.EmploymentStatus;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.payroll.domain.EmployeeCompensation;
import az.millers.hcm.payroll.service.CompensationService;
import az.millers.hcm.security.scope.AccessScopeService;
import az.millers.hcm.staffing.domain.Grade;
import az.millers.hcm.staffing.domain.Position;
import az.millers.hcm.staffing.repo.GradeRepository;
import az.millers.hcm.staffing.repo.PositionRepository;

/**
 * M370 — Compensation dashboard + analytics (read-only aggregations).
 * Reuses CompRatioService risk bands for compa-ratio distribution.
 */
@Service
public class CompensationDashboardService {

    private static final String TENANT = "default";

    private final EmployeeRepository employees;
    private final CompensationService compensations;
    private final PositionRepository positions;
    private final GradeRepository grades;
    private final PayBandService payBandService;
    private final SalaryChangeRequestRepository salaryChangeRepo;
    private final CompensationExceptionRepository exceptionRepo;
    private final CompensationBudgetRepository budgetRepo;
    private final AccessScopeService accessScope;

    public CompensationDashboardService(EmployeeRepository employees,
                                         CompensationService compensations,
                                         PositionRepository positions,
                                         GradeRepository grades,
                                         PayBandService payBandService,
                                         SalaryChangeRequestRepository salaryChangeRepo,
                                         CompensationExceptionRepository exceptionRepo,
                                         CompensationBudgetRepository budgetRepo,
                                         AccessScopeService accessScope) {
        this.employees = employees;
        this.compensations = compensations;
        this.positions = positions;
        this.grades = grades;
        this.payBandService = payBandService;
        this.salaryChangeRepo = salaryChangeRepo;
        this.exceptionRepo = exceptionRepo;
        this.budgetRepo = budgetRepo;
        this.accessScope = accessScope;
    }

    @Transactional(readOnly = true)
    public CompensationDashboardDto dashboard() {
        // Scope: filter employees accessible to current user
        Set<UUID> scopedIds = accessScope.scopeForCurrentUser().orElse(null);

        // Load active employees within scope
        List<Employee> all = employees.findAll().stream()
                .filter(e -> (e.getEmploymentStatus() == EmploymentStatus.ACTIVE
                        || e.getEmploymentStatus() == EmploymentStatus.ON_LEAVE
                        || e.getEmploymentStatus() == EmploymentStatus.SUSPENDED)
                        && (scopedIds == null || scopedIds.contains(e.getId())))
                .toList();

        LocalDate today = LocalDate.now();

        // Pre-load positions and grades
        Map<UUID, Grade> gradeByPosition = new HashMap<>();
        Map<UUID, Grade> gradeById = new HashMap<>();
        for (Grade g : grades.findAll()) {
            gradeById.put(g.getId(), g);
        }
        for (Position p : positions.findAll()) {
            if (p.getGradeId() != null) {
                Grade g = gradeById.get(p.getGradeId());
                if (g != null) gradeByPosition.put(p.getId(), g);
            }
        }

        // Analyze each employee
        int totalWithComp = 0;
        int withoutGrade = 0;
        int withoutBand = 0;
        int outOfBand = 0;

        Map<String, Integer> compaRatioBuckets = new LinkedHashMap<>();
        compaRatioBuckets.put("belowMin", 0);
        compaRatioBuckets.put("low", 0);
        compaRatioBuckets.put("mid", 0);
        compaRatioBuckets.put("high", 0);
        compaRatioBuckets.put("aboveMax", 0);

        for (Employee emp : all) {
            EmployeeCompensation comp = null;
            try {
                comp = compensations.getActiveOn(emp.getId(), today);
            } catch (Exception e) {
                // No comp record
            }
            if (comp == null) continue;

            totalWithComp++;

            Grade grade = null;
            if (emp.getPositionId() != null) {
                grade = gradeByPosition.get(emp.getPositionId());
            }
            if (grade == null) {
                withoutGrade++;
                continue;
            }

            // Find band
            List<PayBand> bands = payBandService.findActiveOnForGrade(grade.getId(), today);
            if (bands.isEmpty()) {
                withoutBand++;
                continue;
            }

            PayBand band = bands.get(0);
            BigDecimal salary = comp.getMonthlyBaseSalary();

            // Check if out of band
            if (salary.compareTo(band.getMinSalary()) < 0 || salary.compareTo(band.getMaxSalary()) > 0) {
                outOfBand++;
            }

            // Compa-ratio distribution (reuse CompRatioService risk logic)
            BigDecimal midpoint = CompRatioService.midpoint(grade);
            if (midpoint != null && midpoint.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal ratio = salary.divide(midpoint, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP);

                CompRiskLevel risk = CompRatioService.riskLevel(ratio);
                switch (risk) {
                    case BELOW_RANGE -> compaRatioBuckets.merge("belowMin", 1, Integer::sum);
                    case LOW_IN_RANGE -> compaRatioBuckets.merge("low", 1, Integer::sum);
                    case AT_MIDPOINT -> compaRatioBuckets.merge("mid", 1, Integer::sum);
                    case HIGH_IN_RANGE -> compaRatioBuckets.merge("high", 1, Integer::sum);
                    case ABOVE_RANGE -> compaRatioBuckets.merge("aboveMax", 1, Integer::sum);
                    default -> {}
                }
            }
        }

        // Pending salary change approvals
        int pendingApprovals = (int) salaryChangeRepo.findByTenantIdAndStatusOrderByCreatedAtDesc(
                TENANT, SalaryChangeStatus.PENDING_APPROVAL).size();

        // Open exceptions
        int openExceptions = (int) exceptionRepo.findByTenantIdAndStatusOrderByRaisedAtDesc(
                TENANT, CompensationExceptionStatus.OPEN).size();

        // Budget utilization
        List<CompensationBudget> activeBudgets = budgetRepo.findByTenantIdAndIsActiveTrueOrderByCreatedAtDesc(TENANT);
        List<BudgetUtilizationRow> budgetRows = activeBudgets.stream().map(b -> {
            BigDecimal remaining = b.getAmount().subtract(b.getConsumedAmount());
            BigDecimal utilizationPct = b.getAmount().compareTo(BigDecimal.ZERO) > 0
                    ? b.getConsumedAmount().divide(b.getAmount(), 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            return new BudgetUtilizationRow(
                    b.getBudgetType().name(),
                    b.getScopeType().name(),
                    b.getScopeRef(),
                    b.getAmount(),
                    b.getConsumedAmount(),
                    remaining,
                    utilizationPct
            );
        }).toList();

        return new CompensationDashboardDto(
                totalWithComp,
                withoutGrade,
                withoutBand,
                outOfBand,
                pendingApprovals,
                openExceptions,
                compaRatioBuckets,
                budgetRows
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Integer> compaRatioDistribution(UUID gradeId) {
        Set<UUID> scopedIds = accessScope.scopeForCurrentUser().orElse(null);
        List<Employee> all = employees.findAll().stream()
                .filter(e -> (e.getEmploymentStatus() == EmploymentStatus.ACTIVE
                        || e.getEmploymentStatus() == EmploymentStatus.ON_LEAVE
                        || e.getEmploymentStatus() == EmploymentStatus.SUSPENDED)
                        && (scopedIds == null || scopedIds.contains(e.getId())))
                .toList();

        LocalDate today = LocalDate.now();

        Map<UUID, Grade> gradeByPosition = new HashMap<>();
        Map<UUID, Grade> gradeById = new HashMap<>();
        for (Grade g : grades.findAll()) {
            gradeById.put(g.getId(), g);
        }
        for (Position p : positions.findAll()) {
            if (p.getGradeId() != null) {
                Grade g = gradeById.get(p.getGradeId());
                if (g != null) gradeByPosition.put(p.getId(), g);
            }
        }

        Map<String, Integer> buckets = new LinkedHashMap<>();
        buckets.put("belowMin", 0);
        buckets.put("low", 0);
        buckets.put("mid", 0);
        buckets.put("high", 0);
        buckets.put("aboveMax", 0);

        for (Employee emp : all) {
            EmployeeCompensation comp = null;
            try {
                comp = compensations.getActiveOn(emp.getId(), today);
            } catch (Exception e) {
            }
            if (comp == null) continue;

            Grade grade = null;
            if (emp.getPositionId() != null) {
                grade = gradeByPosition.get(emp.getPositionId());
            }
            if (grade == null) continue;
            if (gradeId != null && !grade.getId().equals(gradeId)) continue;

            BigDecimal midpoint = CompRatioService.midpoint(grade);
            if (midpoint == null || midpoint.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal ratio = comp.getMonthlyBaseSalary().divide(midpoint, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);

            CompRiskLevel risk = CompRatioService.riskLevel(ratio);
            switch (risk) {
                case BELOW_RANGE -> buckets.merge("belowMin", 1, Integer::sum);
                case LOW_IN_RANGE -> buckets.merge("low", 1, Integer::sum);
                case AT_MIDPOINT -> buckets.merge("mid", 1, Integer::sum);
                case HIGH_IN_RANGE -> buckets.merge("high", 1, Integer::sum);
                case ABOVE_RANGE -> buckets.merge("aboveMax", 1, Integer::sum);
                default -> {}
            }
        }

        return buckets;
    }

    @Transactional(readOnly = true)
    public List<OutOfBandReportRow> outOfBandReport() {
        Set<UUID> scopedIds = accessScope.scopeForCurrentUser().orElse(null);
        List<Employee> all = employees.findAll().stream()
                .filter(e -> (e.getEmploymentStatus() == EmploymentStatus.ACTIVE
                        || e.getEmploymentStatus() == EmploymentStatus.ON_LEAVE
                        || e.getEmploymentStatus() == EmploymentStatus.SUSPENDED)
                        && (scopedIds == null || scopedIds.contains(e.getId())))
                .toList();

        LocalDate today = LocalDate.now();

        Map<UUID, Grade> gradeByPosition = new HashMap<>();
        Map<UUID, Grade> gradeById = new HashMap<>();
        for (Grade g : grades.findAll()) {
            gradeById.put(g.getId(), g);
        }
        for (Position p : positions.findAll()) {
            if (p.getGradeId() != null) {
                Grade g = gradeById.get(p.getGradeId());
                if (g != null) gradeByPosition.put(p.getId(), g);
            }
        }

        List<OutOfBandReportRow> rows = new ArrayList<>();
        // Salary confidentiality: mask raw pay figures from under-privileged readers.
        boolean seeAmounts = az.millers.hcm.compensation.security.CompensationAccessRoles.canSeeSalaryAmounts();

        for (Employee emp : all) {
            EmployeeCompensation comp = null;
            try {
                comp = compensations.getActiveOn(emp.getId(), today);
            } catch (Exception e) {
            }
            if (comp == null) continue;

            Grade grade = null;
            if (emp.getPositionId() != null) {
                grade = gradeByPosition.get(emp.getPositionId());
            }
            if (grade == null) continue;

            List<PayBand> bands = payBandService.findActiveOnForGrade(grade.getId(), today);
            if (bands.isEmpty()) continue;

            PayBand band = bands.get(0);
            BigDecimal salary = comp.getMonthlyBaseSalary();

            // Only include out-of-band
            if (salary.compareTo(band.getMinSalary()) < 0 || salary.compareTo(band.getMaxSalary()) > 0) {
                BigDecimal deltaFromMin = salary.subtract(band.getMinSalary());
                BigDecimal deltaFromMax = salary.subtract(band.getMaxSalary());
                String position = salary.compareTo(band.getMinSalary()) < 0 ? "BELOW_MIN" : "ABOVE_MAX";

                rows.add(new OutOfBandReportRow(
                        emp.getId(),
                        emp.getEmployeeNo(),
                        emp.getFirstName() + " " + emp.getLastName(),
                        emp.getDepartmentName(),
                        grade.getCode(),
                        band.getCode(),
                        band.getMinSalary(),
                        band.getMaxSalary(),
                        seeAmounts ? salary : null,
                        seeAmounts ? deltaFromMin : null,
                        seeAmounts ? deltaFromMax : null,
                        position
                ));
            }
        }

        return rows;
    }

    @Transactional(readOnly = true)
    public List<BudgetUtilizationRow> budgetVsActual() {
        List<CompensationBudget> budgets = budgetRepo.findByTenantIdAndIsActiveTrueOrderByCreatedAtDesc(TENANT);
        return budgets.stream().map(b -> {
            BigDecimal remaining = b.getAmount().subtract(b.getConsumedAmount());
            BigDecimal utilizationPct = b.getAmount().compareTo(BigDecimal.ZERO) > 0
                    ? b.getConsumedAmount().divide(b.getAmount(), 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            return new BudgetUtilizationRow(
                    b.getBudgetType().name(),
                    b.getScopeType().name(),
                    b.getScopeRef(),
                    b.getAmount(),
                    b.getConsumedAmount(),
                    remaining,
                    utilizationPct
            );
        }).toList();
    }
}
