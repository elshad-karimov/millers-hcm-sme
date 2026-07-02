package az.millers.hcm.compbenefits.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.compbenefits.api.dto.CompRatioDtos.CompRatioReport;
import az.millers.hcm.compbenefits.api.dto.CompRatioDtos.CompRiskLevel;
import az.millers.hcm.compbenefits.api.dto.CompRatioDtos.EmployeeCompRatioRow;
import az.millers.hcm.compbenefits.api.dto.CompRatioDtos.GradeBandRow;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.domain.EmploymentStatus;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.staffing.domain.Grade;
import az.millers.hcm.staffing.domain.Position;
import az.millers.hcm.payroll.service.CompensationService;
import az.millers.hcm.staffing.repo.GradeRepository;
import az.millers.hcm.staffing.repo.PositionRepository;

/**
 * Comp-ratio analytics (M102).
 *
 * <p>Comp ratio = (actual base salary / grade midpoint) × 100.
 * Midpoint = (minSalary + maxSalary) / 2.  When a grade has only one
 * bound the midpoint is that bound.  When a grade has no bounds the
 * employee is bucketed as {@link CompRiskLevel#NO_BAND}.
 *
 * <p>The report is in-memory because the employee population is bounded
 * (PRD § 15: ≤ 10 000 active) and the join is straightforward.  A future
 * optimisation can push it into a native SQL window query if response
 * latency becomes an issue.
 *
 * <p>Only ACTIVE employees are included; ON_LEAVE / SUSPENDED are also
 * included by default because their salary still runs.
 */
@Service
public class CompRatioService {

    /** Threshold for flight-risk flag (comp ratio below this = BELOW_RANGE). */
    private static final BigDecimal LOW_THRESHOLD = BigDecimal.valueOf(80);
    /** High in range. */
    private static final BigDecimal HIGH_THRESHOLD = BigDecimal.valueOf(110);
    /** Above range — equity concern. */
    private static final BigDecimal ABOVE_THRESHOLD = BigDecimal.valueOf(120);
    /** Low in range (80–90). */
    private static final BigDecimal LOW_IN_THRESHOLD = BigDecimal.valueOf(90);
    /** Mid-band ceiling. */
    private static final BigDecimal MID_THRESHOLD = BigDecimal.valueOf(110);

    private final EmployeeRepository employees;
    private final PositionRepository positions;
    private final GradeRepository grades;
    private final CompensationService compensations;

    public CompRatioService(EmployeeRepository employees,
                             PositionRepository positions,
                             GradeRepository grades,
                             CompensationService compensations) {
        this.employees = employees;
        this.positions = positions;
        this.grades = grades;
        this.compensations = compensations;
    }

    @Transactional(readOnly = true)
    public CompRatioReport report() {
        // Load every active / on-leave employee (salary is still running).
        List<Employee> all = employees.findAll().stream()
                .filter(e -> e.getEmploymentStatus() == EmploymentStatus.ACTIVE
                        || e.getEmploymentStatus() == EmploymentStatus.ON_LEAVE
                        || e.getEmploymentStatus() == EmploymentStatus.SUSPENDED)
                .toList();
        LocalDate today = LocalDate.now();

        // Pre-build position → grade map to avoid N+1 queries.
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

        List<EmployeeCompRatioRow> rows = new ArrayList<>(all.size());
        int noGrade = 0, noBand = 0, flightRisk = 0;
        BigDecimal ratioSum = BigDecimal.ZERO;
        int ratioCount = 0;

        // Accumulate per-grade stats for the band rollup.
        Map<String, List<EmployeeCompRatioRow>> byGrade = new LinkedHashMap<>();

        for (Employee emp : all) {
            Grade grade = null;
            if (emp.getPositionId() != null) {
                grade = gradeByPosition.get(emp.getPositionId());
            }
            // Salary from the effective-dated compensation record for today.
            var comp = compensations.getActiveOn(emp.getId(), today);
            BigDecimal actual = comp != null ? comp.getMonthlyBaseSalary() : null;
            if (actual == null || actual.compareTo(BigDecimal.ZERO) == 0) {
                noGrade++;
                rows.add(noGradeRow(emp));
                continue;
            }
            if (grade == null) {
                noGrade++;
                rows.add(noGradeRow(emp, actual));
                continue;
            }
            BigDecimal mid = midpoint(grade);
            EmployeeCompRatioRow row = buildRow(emp, grade, mid, actual);
            rows.add(row);
            byGrade.computeIfAbsent(grade.getCode(), k -> new ArrayList<>()).add(row);
            if (row.riskLevel() == CompRiskLevel.NO_BAND) {
                noBand++;
            } else if (row.riskLevel() == CompRiskLevel.BELOW_RANGE) {
                flightRisk++;
            }
            if (row.compRatio() != null) {
                ratioSum = ratioSum.add(row.compRatio());
                ratioCount++;
            }
        }

        BigDecimal avgRatio = ratioCount > 0
                ? ratioSum.divide(BigDecimal.valueOf(ratioCount), 2, RoundingMode.HALF_UP)
                : null;

        // Sort employees: flight-risk first, then by comp-ratio asc.
        rows.sort(Comparator
                .comparing((EmployeeCompRatioRow r) -> r.riskLevel().ordinal())
                .thenComparing(Comparator.comparing(
                        r -> r.compRatio() == null ? BigDecimal.valueOf(999) : r.compRatio())));

        List<GradeBandRow> bands = new ArrayList<>(byGrade.size());
        for (Map.Entry<String, List<EmployeeCompRatioRow>> e : byGrade.entrySet()) {
            bands.add(buildBand(e.getKey(), e.getValue()));
        }
        bands.sort(Comparator.comparing(GradeBandRow::gradeCode));

        return new CompRatioReport(all.size(), noGrade, noBand, avgRatio, flightRisk, rows, bands);
    }

    // ── Per-employee row ───────────────────────────────────────────────────

    private EmployeeCompRatioRow buildRow(Employee emp, Grade grade, BigDecimal mid,
                                           BigDecimal actual) {
        BigDecimal ratio = null;
        BigDecimal vs = null;
        CompRiskLevel level = CompRiskLevel.NO_BAND;
        if (mid != null && mid.compareTo(BigDecimal.ZERO) > 0) {
            ratio = actual.divide(mid, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
            vs = actual.subtract(mid).setScale(2, RoundingMode.HALF_UP);
            level = riskLevel(ratio);
        }
        return new EmployeeCompRatioRow(
                emp.getId(), emp.getEmployeeNo(),
                name(emp), emp.getDepartmentName(),
                grade.getCode(), grade.getName(),
                grade.getMinSalary(), mid, grade.getMaxSalary(),
                actual, ratio, vs, level);
    }

    private EmployeeCompRatioRow noGradeRow(Employee emp) {
        return noGradeRow(emp, null);
    }

    private EmployeeCompRatioRow noGradeRow(Employee emp, BigDecimal actual) {
        return new EmployeeCompRatioRow(
                emp.getId(), emp.getEmployeeNo(), name(emp),
                emp.getDepartmentName(),
                null, null, null, null, null,
                actual, null, null, CompRiskLevel.NO_BAND);
    }

    // ── Grade band rollup ──────────────────────────────────────────────────

    private GradeBandRow buildBand(String gradeCode, List<EmployeeCompRatioRow> empRows) {
        String gradeName = empRows.get(0).gradeName();
        int count = empRows.size();
        BigDecimal avgActual = avg(empRows.stream()
                .map(EmployeeCompRatioRow::actualSalary).toList());
        BigDecimal avgRatio = avg(empRows.stream()
                .filter(r -> r.compRatio() != null)
                .map(EmployeeCompRatioRow::compRatio).toList());
        int below = (int) empRows.stream()
                .filter(r -> r.riskLevel() == CompRiskLevel.BELOW_RANGE).count();
        int atMid = (int) empRows.stream()
                .filter(r -> r.riskLevel() == CompRiskLevel.AT_MIDPOINT).count();
        int above = (int) empRows.stream()
                .filter(r -> r.riskLevel() == CompRiskLevel.ABOVE_RANGE
                        || r.riskLevel() == CompRiskLevel.HIGH_IN_RANGE).count();
        EmployeeCompRatioRow first = empRows.get(0);
        return new GradeBandRow(gradeCode, gradeName, count,
                first.minSalary(), first.midpointSalary(), first.maxSalary(),
                avgActual, avgRatio, below, atMid, above);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /** Public for reuse by M370 compensation dashboard. */
    public static CompRiskLevel riskLevel(BigDecimal ratio) {
        if (ratio == null) return CompRiskLevel.NO_BAND;
        if (ratio.compareTo(LOW_THRESHOLD) < 0)   return CompRiskLevel.BELOW_RANGE;
        if (ratio.compareTo(LOW_IN_THRESHOLD) < 0) return CompRiskLevel.LOW_IN_RANGE;
        if (ratio.compareTo(MID_THRESHOLD) <= 0)  return CompRiskLevel.AT_MIDPOINT;
        if (ratio.compareTo(ABOVE_THRESHOLD) <= 0) return CompRiskLevel.HIGH_IN_RANGE;
        return CompRiskLevel.ABOVE_RANGE;
    }

    /** Public for reuse by M370 compensation dashboard. */
    public static BigDecimal midpoint(Grade grade) {
        BigDecimal min = grade.getMinSalary();
        BigDecimal max = grade.getMaxSalary();
        if (min == null && max == null) return null;
        if (min == null) return max;
        if (max == null) return min;
        return min.add(max).divide(BigDecimal.TWO, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal avg(List<BigDecimal> vals) {
        if (vals == null || vals.isEmpty()) return null;
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal v : vals) sum = sum.add(v);
        return sum.divide(BigDecimal.valueOf(vals.size()), 2, RoundingMode.HALF_UP);
    }

    private static String name(Employee e) {
        return ((e.getFirstName() == null ? "" : e.getFirstName()) + " "
                + (e.getLastName() == null ? "" : e.getLastName())).trim();
    }
}
