package az.millers.hcm.compensation.service;
import az.millers.hcm.common.tenant.TenantContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.compensation.api.dto.MarketComparisonDto;
import az.millers.hcm.compensation.domain.MarketSalaryData;
import az.millers.hcm.compensation.domain.MarketSalarySurvey;
import az.millers.hcm.compensation.repo.MarketSalaryDataRepository;
import az.millers.hcm.compensation.repo.MarketSalarySurveyRepository;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.payroll.domain.EmployeeCompensation;
import az.millers.hcm.payroll.service.CompensationService;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.scope.AccessScopeService;
import az.millers.hcm.staffing.domain.Grade;
import az.millers.hcm.staffing.domain.Position;
import az.millers.hcm.staffing.repo.GradeRepository;
import az.millers.hcm.staffing.repo.PositionRepository;

/**
 * M367 — Market salary survey + data service.
 */
@Service
public class MarketDataService {

    private static final String MODULE = "compensation";

    private final MarketSalarySurveyRepository surveys;
    private final MarketSalaryDataRepository marketData;
    private final EmployeeRepository employees;
    private final CompensationService compensationService;
    private final PositionRepository positions;
    private final GradeRepository grades;
    private final AccessScopeService accessScope;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public MarketDataService(MarketSalarySurveyRepository surveys,
                              MarketSalaryDataRepository marketData,
                              EmployeeRepository employees,
                              CompensationService compensationService,
                              PositionRepository positions,
                              GradeRepository grades,
                              AccessScopeService accessScope,
                              AuditService audit,
                              CurrentRequest currentRequest) {
        this.surveys = surveys;
        this.marketData = marketData;
        this.employees = employees;
        this.compensationService = compensationService;
        this.positions = positions;
        this.grades = grades;
        this.accessScope = accessScope;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Survey CRUD
    // ══════════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<MarketSalarySurvey> listSurveys() {
        return surveys.findByTenantIdOrderBySurveyYearDesc(TenantContext.current());
    }

    @Transactional(readOnly = true)
    public MarketSalarySurvey getSurvey(UUID id) {
        return surveys.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Market survey not found: " + id));
    }

    @Transactional
    public MarketSalarySurvey createSurvey(String provider, int surveyYear, String country, String currency, String notes) {
        MarketSalarySurvey survey = new MarketSalarySurvey();
        survey.setTenantId(TenantContext.current());
        survey.setProvider(provider);
        survey.setSurveyYear(surveyYear);
        survey.setCountry(country);
        survey.setCurrency(currency);
        survey.setNotes(notes);

        MarketSalarySurvey saved = surveys.save(survey);
        audit.record(MODULE, "MarketSalarySurvey", saved.getId().toString(),
                "CREATE", null,
                Map.of("provider", provider, "year", surveyYear));
        return saved;
    }

    @Transactional
    public void deleteSurvey(UUID id) {
        MarketSalarySurvey survey = getSurvey(id);
        surveys.delete(survey);
        audit.record(MODULE, "MarketSalarySurvey", id.toString(),
                "DELETE", null,
                Map.of("provider", survey.getProvider(), "year", survey.getSurveyYear()));
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Market Data CRUD
    // ══════════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<MarketSalaryData> listData(UUID surveyId) {
        getSurvey(surveyId); // ensure survey exists
        return marketData.findByTenantIdAndSurveyIdOrderByGradeCodeAscJobCodeAsc(TenantContext.current(), surveyId);
    }

    @Transactional
    public MarketSalaryData addData(UUID surveyId, String jobCode, String gradeCode, String location,
                                     BigDecimal p25, BigDecimal p50, BigDecimal p75, BigDecimal p90, String currency) {
        getSurvey(surveyId); // ensure survey exists

        // Validate percentiles
        if (p25 != null && p50 != null && p25.compareTo(p50) > 0) {
            throw new BadRequestException("p25 must be <= p50");
        }
        if (p50 != null && p75 != null && p50.compareTo(p75) > 0) {
            throw new BadRequestException("p50 must be <= p75");
        }
        if (p75 != null && p90 != null && p75.compareTo(p90) > 0) {
            throw new BadRequestException("p75 must be <= p90");
        }

        MarketSalaryData data = new MarketSalaryData();
        data.setTenantId(TenantContext.current());
        data.setSurveyId(surveyId);
        data.setJobCode(jobCode);
        data.setGradeCode(gradeCode);
        data.setLocation(location);
        data.setP25(p25);
        data.setP50(p50);
        data.setP75(p75);
        data.setP90(p90);
        data.setCurrency(currency);

        MarketSalaryData saved = marketData.save(data);
        audit.record(MODULE, "MarketSalaryData", saved.getId().toString(),
                "CREATE", null,
                Map.of("surveyId", surveyId.toString(), "gradeCode", gradeCode != null ? gradeCode : "",
                        "jobCode", jobCode != null ? jobCode : ""));
        return saved;
    }

    @Transactional
    public void deleteData(UUID id) {
        MarketSalaryData data = marketData.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Market data not found: " + id));
        marketData.delete(data);
        audit.record(MODULE, "MarketSalaryData", id.toString(), "DELETE", null, null);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Market Comparison
    // ══════════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public MarketComparisonDto compare(UUID employeeId, UUID surveyId) {
        // Enforce hierarchy access
        if (!accessScope.isAccessible(employeeId)) {
            throw new BadRequestException("Access denied to employee: " + employeeId);
        }

        Employee emp = employees.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));

        // Resolve current salary
        EmployeeCompensation currentComp;
        try {
            currentComp = compensationService.getActiveOn(employeeId, LocalDate.now());
        } catch (Exception e) {
            throw new BadRequestException("Employee has no active compensation record");
        }

        BigDecimal currentSalary = currentComp.getMonthlyBaseSalary();

        // Resolve grade code
        String gradeCode = null;
        if (emp.getPositionId() != null) {
            Position pos = positions.findById(emp.getPositionId()).orElse(null);
            if (pos != null && pos.getGradeId() != null) {
                Grade grade = grades.findById(pos.getGradeId()).orElse(null);
                if (grade != null) {
                    gradeCode = grade.getCode();
                }
            }
        }

        if (gradeCode == null) {
            throw new BadRequestException("Employee has no grade assigned");
        }

        // Resolve survey (latest if null)
        MarketSalarySurvey survey;
        if (surveyId == null) {
            survey = surveys.findFirstByTenantIdOrderBySurveyYearDesc(TenantContext.current())
                    .orElseThrow(() -> new ResourceNotFoundException("No market surveys available"));
        } else {
            survey = getSurvey(surveyId);
        }

        // Find market data by grade code (fallback to job_code if needed)
        MarketSalaryData data = marketData.findFirstByTenantIdAndSurveyIdAndGradeCode(TenantContext.current(), survey.getId(), gradeCode)
                .orElse(null);

        if (data == null) {
            throw new ResourceNotFoundException("No market data for grade: " + gradeCode);
        }

        // Calculate market ratio and position
        BigDecimal marketRatio = null;
        String positionVsMarket = null;

        if (data.getP50() != null && data.getP50().compareTo(BigDecimal.ZERO) > 0) {
            marketRatio = currentSalary.divide(data.getP50(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);

            if (currentSalary.compareTo(data.getP25()) < 0) {
                positionVsMarket = "BELOW_P25";
            } else if (currentSalary.compareTo(data.getP50()) < 0) {
                positionVsMarket = "P25_TO_P50";
            } else if (currentSalary.compareTo(data.getP75()) < 0) {
                positionVsMarket = "P50_TO_P75";
            } else if (currentSalary.compareTo(data.getP90()) < 0) {
                positionVsMarket = "P75_TO_P90";
            } else {
                positionVsMarket = "ABOVE_P90";
            }
        }

        return new MarketComparisonDto(
            currentSalary,
            gradeCode,
            survey.getProvider(),
            survey.getSurveyYear(),
            data.getP25(),
            data.getP50(),
            data.getP75(),
            data.getP90(),
            marketRatio,
            positionVsMarket
        );
    }
}
