package az.millers.hcm.compensation.service;
import az.millers.hcm.common.tenant.TenantContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.compensation.domain.MeritMatrix;
import az.millers.hcm.compensation.domain.MeritMatrixCell;
import az.millers.hcm.compensation.domain.PayBand;
import az.millers.hcm.compensation.repo.MeritMatrixCellRepository;
import az.millers.hcm.compensation.repo.MeritMatrixRepository;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.payroll.domain.EmployeeCompensation;
import az.millers.hcm.payroll.service.CompensationService;
import az.millers.hcm.performance.domain.PerformanceReview;
import az.millers.hcm.performance.repo.PerformanceReviewRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.scope.AccessScopeService;
import az.millers.hcm.staffing.domain.Grade;
import az.millers.hcm.staffing.domain.Position;
import az.millers.hcm.staffing.repo.GradeRepository;
import az.millers.hcm.staffing.repo.PositionRepository;

/**
 * M363 — Merit matrix service: CRUD + suggest merit % from performance × range position.
 */
@Service
public class MeritMatrixService {

    private static final String MODULE = "compensation";
    private static final String ENTITY = "MeritMatrix";

    // Range penetration thresholds
    private static final BigDecimal LOW_THRESHOLD = new BigDecimal("33.34");
    private static final BigDecimal HIGH_THRESHOLD = new BigDecimal("66.66");

    // Performance rating thresholds
    private static final BigDecimal EXCELLENT_THRESHOLD = new BigDecimal("4.5");
    private static final BigDecimal GOOD_THRESHOLD = new BigDecimal("3.5");
    private static final BigDecimal MEETS_THRESHOLD = new BigDecimal("2.5");

    private final MeritMatrixRepository matrixRepo;
    private final MeritMatrixCellRepository cellRepo;
    private final EmployeeRepository employees;
    private final CompensationService compensations;
    private final PayBandService payBandService;
    private final PositionRepository positions;
    private final GradeRepository grades;
    private final PerformanceReviewRepository performanceReviews;
    private final AccessScopeService accessScope;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public MeritMatrixService(MeritMatrixRepository matrixRepo,
                               MeritMatrixCellRepository cellRepo,
                               EmployeeRepository employees,
                               CompensationService compensations,
                               PayBandService payBandService,
                               PositionRepository positions,
                               GradeRepository grades,
                               PerformanceReviewRepository performanceReviews,
                               AccessScopeService accessScope,
                               AuditService audit,
                               CurrentRequest currentRequest) {
        this.matrixRepo = matrixRepo;
        this.cellRepo = cellRepo;
        this.employees = employees;
        this.compensations = compensations;
        this.payBandService = payBandService;
        this.positions = positions;
        this.grades = grades;
        this.performanceReviews = performanceReviews;
        this.accessScope = accessScope;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<MeritMatrix> listActive() {
        return matrixRepo.findByTenantIdAndIsActiveTrue(TenantContext.current());
    }

    @Transactional(readOnly = true)
    public MeritMatrix get(UUID id) {
        return matrixRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Merit matrix not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<MeritMatrixCell> getCells(UUID matrixId) {
        return cellRepo.findByMatrixId(matrixId);
    }

    @Transactional
    public MeritMatrix create(String code, String name) {
        if (matrixRepo.findByTenantIdAndCode(TenantContext.current(), code).isPresent()) {
            throw new BadRequestException("MATRIX_CODE_DUPLICATE");
        }

        MeritMatrix matrix = new MeritMatrix();
        matrix.setTenantId(TenantContext.current());
        matrix.setCode(code);
        matrix.setName(name);

        MeritMatrix saved = matrixRepo.save(matrix);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, Map.of("code", code, "name", name));
        return saved;
    }

    @Transactional
    public MeritMatrix updateCells(UUID matrixId, List<MeritMatrixCell> cells) {
        MeritMatrix matrix = get(matrixId);

        // Delete existing cells
        cellRepo.deleteByMatrixId(matrixId);

        // Insert new cells
        for (MeritMatrixCell cell : cells) {
            cell.setId(null);
            cell.setTenantId(TenantContext.current());
            cell.setMatrixId(matrixId);
            cellRepo.save(cell);
        }

        audit.record(MODULE, ENTITY, matrixId.toString(),
                "UPDATE_CELLS", null, Map.of("cellCount", cells.size()));
        return matrix;
    }

    @Transactional
    public void deactivate(UUID id) {
        MeritMatrix matrix = get(id);
        matrix.setIsActive(false);
        matrixRepo.save(matrix);
        audit.record(MODULE, ENTITY, id.toString(), "DEACTIVATE", null, null);
    }

    /**
     * Suggest merit % for an employee based on their performance rating and range position.
     * Returns { performanceBand, rangePosition, meritPct, currentSalary, meritAmount, newSalary }.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> suggest(UUID employeeId, UUID matrixIdOrNull) {
        // Enforce hierarchy access
        if (!accessScope.isAccessible(employeeId)) {
            throw new BadRequestException("Access denied to employee: " + employeeId);
        }

        Employee emp = employees.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));

        // Get current salary
        EmployeeCompensation currentComp;
        try {
            currentComp = compensations.getActiveOn(employeeId, LocalDate.now());
        } catch (Exception e) {
            throw new BadRequestException("Employee has no active compensation record");
        }

        BigDecimal currentSalary = currentComp.getMonthlyBaseSalary();

        // Resolve grade and pay band (reuse CompensationProfileService logic)
        Grade grade = resolveGrade(emp);
        if (grade == null) {
            throw new BadRequestException("Employee has no grade assigned");
        }

        List<PayBand> bands = payBandService.findActiveOnForGrade(grade.getId(), LocalDate.now());
        if (bands.isEmpty()) {
            throw new BadRequestException("No active pay band found for employee's grade");
        }
        PayBand band = bands.get(0);

        BigDecimal min = band.getMinSalary();
        BigDecimal max = band.getMaxSalary();

        // Calculate range penetration
        BigDecimal range = max.subtract(min);
        if (range.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Invalid pay band range");
        }

        BigDecimal rangePenetration = currentSalary.subtract(min)
                .divide(range, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);

        // Map range penetration to range position (LOW < 33.34, MID 33.34-66.66, HIGH > 66.66)
        String rangePosition;
        if (rangePenetration.compareTo(LOW_THRESHOLD) < 0) {
            rangePosition = "LOW";
        } else if (rangePenetration.compareTo(HIGH_THRESHOLD) <= 0) {
            rangePosition = "MID";
        } else {
            rangePosition = "HIGH";
        }

        // Get latest performance review final rating
        PerformanceReview latestReview = performanceReviews.findByEmployeeIdOrderByCreatedAtDesc(
                employeeId, PageRequest.of(0, 1)).stream().findFirst().orElse(null);

        BigDecimal finalRating = latestReview != null ? latestReview.getFinalRating() : null;

        // Map finalRating to performance band (>=4.5 EXCELLENT, >=3.5 GOOD, >=2.5 MEETS, else BELOW)
        String performanceBand;
        if (finalRating == null) {
            performanceBand = "MEETS"; // Default if no rating
        } else if (finalRating.compareTo(EXCELLENT_THRESHOLD) >= 0) {
            performanceBand = "EXCELLENT";
        } else if (finalRating.compareTo(GOOD_THRESHOLD) >= 0) {
            performanceBand = "GOOD";
        } else if (finalRating.compareTo(MEETS_THRESHOLD) >= 0) {
            performanceBand = "MEETS";
        } else {
            performanceBand = "BELOW";
        }

        // Resolve matrix
        MeritMatrix matrix;
        if (matrixIdOrNull != null) {
            matrix = get(matrixIdOrNull);
        } else {
            // Default to the first active matrix with code DEFAULT
            matrix = matrixRepo.findByTenantIdAndCode(TenantContext.current(), "DEFAULT")
                    .or(() -> matrixRepo.findFirstByTenantIdAndIsActiveTrueOrderByCreatedAtAsc(TenantContext.current()))
                    .orElseThrow(() -> new BadRequestException("No active merit matrix found"));
        }

        // Look up merit %
        MeritMatrixCell cell = cellRepo.findByMatrixIdAndPerformanceBandAndRangePosition(
                matrix.getId(), performanceBand, rangePosition)
                .orElseThrow(() -> new BadRequestException(
                        "No merit matrix cell found for " + performanceBand + " × " + rangePosition));

        BigDecimal meritPct = cell.getMeritPct();
        BigDecimal meritAmount = currentSalary.multiply(meritPct)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal newSalary = currentSalary.add(meritAmount);

        return Map.of(
                "performanceBand", performanceBand,
                "rangePosition", rangePosition,
                "meritPct", meritPct,
                "currentSalary", currentSalary,
                "meritAmount", meritAmount,
                "newSalary", newSalary,
                "finalRating", finalRating != null ? finalRating : BigDecimal.ZERO,
                "rangePenetration", rangePenetration
        );
    }

    private Grade resolveGrade(Employee emp) {
        if (emp.getPositionId() == null) return null;
        Position pos = positions.findById(emp.getPositionId()).orElse(null);
        if (pos == null || pos.getGradeId() == null) return null;
        return grades.findById(pos.getGradeId()).orElse(null);
    }
}
