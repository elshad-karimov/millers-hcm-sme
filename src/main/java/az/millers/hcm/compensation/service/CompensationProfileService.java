package az.millers.hcm.compensation.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.compensation.api.dto.CompensationProfileDto;
import az.millers.hcm.compensation.api.dto.CompensationProfileDto.BandInfo;
import az.millers.hcm.compensation.api.dto.CompensationProfileDto.CurrentSalaryInfo;
import az.millers.hcm.compensation.api.dto.CompensationProfileDto.GradeInfo;
import az.millers.hcm.compensation.api.dto.CompensationProfileDto.SalaryHistoryItem;
import az.millers.hcm.compensation.domain.PayBand;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.payroll.domain.EmployeeCompensation;
import az.millers.hcm.payroll.service.CompensationService;
import az.millers.hcm.security.scope.AccessScopeService;
import az.millers.hcm.staffing.domain.Grade;
import az.millers.hcm.staffing.domain.Position;
import az.millers.hcm.staffing.repo.GradeRepository;
import az.millers.hcm.staffing.repo.PositionRepository;

/**
 * M360 — Employee compensation profile service.
 * Assembles current salary, grade, band, compa-ratio, range penetration, and history.
 * Reuses CompRatioService's midpoint formula and existing services.
 */
@Service
public class CompensationProfileService {

    private static final String TENANT = "default";

    private final EmployeeRepository employees;
    private final CompensationService compensations;
    private final PositionRepository positions;
    private final GradeRepository grades;
    private final PayBandService payBands;
    private final AccessScopeService accessScope;

    public CompensationProfileService(EmployeeRepository employees,
                                       CompensationService compensations,
                                       PositionRepository positions,
                                       GradeRepository grades,
                                       PayBandService payBands,
                                       AccessScopeService accessScope) {
        this.employees = employees;
        this.compensations = compensations;
        this.positions = positions;
        this.grades = grades;
        this.payBands = payBands;
        this.accessScope = accessScope;
    }

    @Transactional(readOnly = true)
    public CompensationProfileDto profile(UUID employeeId) {
        // Enforce hierarchy access (manager/self or unrestricted)
        if (!accessScope.isAccessible(employeeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to employee: " + employeeId);
        }

        Employee emp = employees.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));

        LocalDate today = LocalDate.now();

        // Current salary from EmployeeCompensation
        EmployeeCompensation currentComp;
        try {
            currentComp = compensations.getActiveOn(employeeId, today);
        } catch (Exception e) {
            // No compensation record
            currentComp = null;
        }

        CurrentSalaryInfo currentSalary = null;
        if (currentComp != null) {
            currentSalary = new CurrentSalaryInfo(
                currentComp.getMonthlyBaseSalary(),
                currentComp.getCurrency(),
                currentComp.getEffectiveFrom()
            );
        }

        // Resolve grade via position (same logic as CompRatioService)
        Grade grade = null;
        if (emp.getPositionId() != null) {
            Position pos = positions.findById(emp.getPositionId()).orElse(null);
            if (pos != null && pos.getGradeId() != null) {
                grade = grades.findById(pos.getGradeId()).orElse(null);
            }
        }

        GradeInfo gradeInfo = null;
        if (grade != null) {
            gradeInfo = new GradeInfo(
                grade.getCode(),
                grade.getName(),
                grade.getMinSalary(),
                grade.getMaxSalary()
            );
        }

        // Find applicable pay band (grade-linked, active on today)
        PayBand band = null;
        if (grade != null) {
            List<PayBand> bands = payBands.findActiveOnForGrade(grade.getId(), today);
            if (!bands.isEmpty()) {
                band = bands.get(0); // First match (most recent effective_from)
            }
        }

        BandInfo bandInfo = null;
        BigDecimal midpoint = null;
        BigDecimal min = null;
        BigDecimal max = null;

        if (band != null) {
            bandInfo = new BandInfo(
                band.getCode(),
                band.getName(),
                band.getMinSalary(),
                band.getMidSalary(),
                band.getMaxSalary(),
                band.getQ1Salary(),
                band.getQ3Salary()
            );
            midpoint = band.getMidSalary();
            min = band.getMinSalary();
            max = band.getMaxSalary();
        } else if (grade != null) {
            // Fallback: use grade's min/max with computed midpoint
            min = grade.getMinSalary();
            max = grade.getMaxSalary();
            if (min != null && max != null) {
                midpoint = min.add(max).divide(BigDecimal.TWO, 2, RoundingMode.HALF_UP);
            } else if (min != null) {
                midpoint = min;
            } else if (max != null) {
                midpoint = max;
            }
        }

        // Calculate compa-ratio and range penetration (reuse CompRatioService logic)
        BigDecimal compaRatio = null;
        BigDecimal rangePenetration = null;
        String rangePositionLabel = null;

        if (currentComp != null && midpoint != null && midpoint.compareTo(BigDecimal.ZERO) > 0) {
            compaRatio = currentComp.getMonthlyBaseSalary()
                    .divide(midpoint, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        if (currentComp != null && min != null && max != null) {
            BigDecimal range = max.subtract(min);
            if (range.compareTo(BigDecimal.ZERO) > 0) {
                rangePenetration = currentComp.getMonthlyBaseSalary().subtract(min)
                        .divide(range, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP);

                // Range position label
                BigDecimal salary = currentComp.getMonthlyBaseSalary();
                if (salary.compareTo(min) < 0) {
                    rangePositionLabel = "BELOW_MIN";
                } else if (rangePenetration.compareTo(BigDecimal.valueOf(25)) < 0) {
                    rangePositionLabel = "LOW";
                } else if (rangePenetration.compareTo(BigDecimal.valueOf(75)) <= 0) {
                    rangePositionLabel = "MID";
                } else if (salary.compareTo(max) <= 0) {
                    rangePositionLabel = "HIGH";
                } else {
                    rangePositionLabel = "ABOVE_MAX";
                }
            }
        }

        // Salary history
        List<EmployeeCompensation> history = compensations.historyFor(employeeId);

        // Salary confidentiality: under-privileged readers (e.g. HR_SPECIALIST) see the
        // structure, grade, band and range position but NOT the employee's raw pay figures.
        boolean seeAmounts = az.millers.hcm.compensation.security.CompensationAccessRoles.canSeeSalaryAmounts();
        List<SalaryHistoryItem> salaryHistory = history.stream()
                .map(c -> new SalaryHistoryItem(
                    c.getEffectiveFrom(),
                    c.getEffectiveTo(),
                    seeAmounts ? c.getMonthlyBaseSalary() : null,
                    c.getCurrency(),
                    c.getReason()
                ))
                .toList();

        if (!seeAmounts && currentSalary != null) {
            currentSalary = new CurrentSalaryInfo(null, currentSalary.currency(), currentSalary.effectiveFrom());
        }

        return new CompensationProfileDto(
            currentSalary,
            gradeInfo,
            bandInfo,
            seeAmounts ? compaRatio : null,
            seeAmounts ? rangePenetration : null,
            rangePositionLabel,
            salaryHistory
        );
    }
}
