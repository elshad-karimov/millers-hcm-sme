package az.millers.hcm.compensation.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.compensation.api.dto.MarketComparisonDto;
import az.millers.hcm.compensation.domain.MarketSalaryData;
import az.millers.hcm.compensation.domain.MarketSalarySurvey;
import az.millers.hcm.compensation.service.MarketDataService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M367 — Market salary survey and data API.
 */
@RestController
@RequestMapping("/api/compensation/market")
public class MarketDataController {

    private final MarketDataService service;

    public MarketDataController(MarketDataService service) {
        this.service = service;
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Surveys
    // ══════════════════════════════════════════════════════════════════════════════

    @GetMapping("/surveys")
    @PreAuthorize(SecurityRoles.READ_COMPENSATION)
    public List<MarketSalarySurvey> listSurveys() {
        return service.listSurveys();
    }

    @GetMapping("/surveys/{id}")
    @PreAuthorize(SecurityRoles.READ_COMPENSATION)
    public MarketSalarySurvey getSurvey(@PathVariable UUID id) {
        return service.getSurvey(id);
    }

    @PostMapping("/surveys")
    @PreAuthorize(SecurityRoles.WRITE_COMPENSATION)
    public MarketSalarySurvey createSurvey(@RequestBody CreateSurveyRequest req) {
        return service.createSurvey(req.provider, req.surveyYear, req.country, req.currency, req.notes);
    }

    @DeleteMapping("/surveys/{id}")
    @PreAuthorize(SecurityRoles.WRITE_COMPENSATION)
    public void deleteSurvey(@PathVariable UUID id) {
        service.deleteSurvey(id);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Market Data
    // ══════════════════════════════════════════════════════════════════════════════

    @GetMapping("/surveys/{surveyId}/data")
    @PreAuthorize(SecurityRoles.READ_COMPENSATION)
    public List<MarketSalaryData> listData(@PathVariable UUID surveyId) {
        return service.listData(surveyId);
    }

    @PostMapping("/surveys/{surveyId}/data")
    @PreAuthorize(SecurityRoles.WRITE_COMPENSATION)
    public MarketSalaryData addData(@PathVariable UUID surveyId, @RequestBody AddMarketDataRequest req) {
        return service.addData(surveyId, req.jobCode, req.gradeCode, req.location,
                req.p25, req.p50, req.p75, req.p90, req.currency);
    }

    @DeleteMapping("/data/{id}")
    @PreAuthorize(SecurityRoles.WRITE_COMPENSATION)
    public void deleteData(@PathVariable UUID id) {
        service.deleteData(id);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Comparison
    // ══════════════════════════════════════════════════════════════════════════════

    @GetMapping("/compare")
    @PreAuthorize(SecurityRoles.READ_COMPENSATION)
    public MarketComparisonDto compare(@RequestParam UUID employeeId, @RequestParam(required = false) UUID surveyId) {
        return service.compare(employeeId, surveyId);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // DTOs
    // ══════════════════════════════════════════════════════════════════════════════

    public record CreateSurveyRequest(String provider, int surveyYear, String country, String currency, String notes) {}

    public record AddMarketDataRequest(String jobCode, String gradeCode, String location,
                                        BigDecimal p25, BigDecimal p50, BigDecimal p75, BigDecimal p90, String currency) {}
}
