package az.millers.hcm.staffing.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.security.SecurityRoles;
import az.millers.hcm.staffing.api.dto.HiringPlanLineResponse;
import az.millers.hcm.staffing.service.HiringPlanService;

/**
 * M422: Hiring plan integration.
 */
@RestController
@RequestMapping("/api/workforce-plans")
public class HiringPlanController {

    private final HiringPlanService service;

    public HiringPlanController(HiringPlanService service) {
        this.service = service;
    }

    @PostMapping("/{id}/hiring-plan")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public List<HiringPlanLineResponse> generateHiringPlan(@PathVariable UUID id) {
        return service.generateFromPlan(id).stream()
                .map(HiringPlanLineResponse::from)
                .toList();
    }

    @GetMapping("/{id}/hiring-plan")
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<HiringPlanLineResponse> getHiringPlan(@PathVariable UUID id) {
        return service.getByPlan(id).stream()
                .map(HiringPlanLineResponse::from)
                .toList();
    }

    @PostMapping("/{id}/hiring-plan/{lineId}/link-vacancy")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public HiringPlanLineResponse linkVacancy(@PathVariable UUID id,
                                               @PathVariable UUID lineId,
                                               @RequestBody LinkVacancyRequest req) {
        return HiringPlanLineResponse.from(service.linkVacancy(lineId, req.vacancyId()));
    }

    public record LinkVacancyRequest(UUID vacancyId) {}
}
