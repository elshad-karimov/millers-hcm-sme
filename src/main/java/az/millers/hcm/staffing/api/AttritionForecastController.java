package az.millers.hcm.staffing.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.security.SecurityRoles;
import az.millers.hcm.staffing.api.dto.AttritionForecastResponse;
import az.millers.hcm.staffing.service.AttritionForecastService;

/**
 * M423: Attrition forecast.
 */
@RestController
@RequestMapping("/api/workforce-plans/attrition-forecast")
public class AttritionForecastController {

    private final AttritionForecastService service;

    public AttritionForecastController(AttritionForecastService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<AttritionForecastResponse> forecast(@RequestParam(required = false) UUID planId,
                                                      @RequestParam(required = false) LocalDate horizonDate) {
        LocalDate horizon = horizonDate != null ? horizonDate : LocalDate.now().plusMonths(12);
        return service.forecast(planId, horizon).stream()
                .map(AttritionForecastResponse::from)
                .toList();
    }

    @GetMapping("/by-plan")
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<AttritionForecastResponse> getByPlan(@RequestParam UUID planId) {
        return service.getByPlan(planId).stream()
                .map(AttritionForecastResponse::from)
                .toList();
    }
}
