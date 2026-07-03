package az.millers.hcm.compbenefits.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.compbenefits.api.dto.BenefitLifeEventDtos.EventRequest;
import az.millers.hcm.compbenefits.api.dto.BenefitLifeEventDtos.EventResponse;
import az.millers.hcm.compbenefits.api.dto.BenefitLifeEventDtos.ReviewRequest;
import az.millers.hcm.compbenefits.service.BenefitLifeEventService;
import az.millers.hcm.security.SecurityRoles;
import jakarta.validation.Valid;

/** Qualifying life-events API (HCM_11 M380). */
@RestController
@RequestMapping("/api/compbenefits/life-events")
public class BenefitLifeEventController {

    private final BenefitLifeEventService service;

    public BenefitLifeEventController(BenefitLifeEventService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_BENEFITS_PLUS_MANAGERS)
    public List<EventResponse> list(@RequestParam(required = false) String status,
                                    @RequestParam(required = false) UUID employeeId) {
        if (employeeId != null) return service.listForEmployee(employeeId);
        return service.list(status);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_BENEFITS)
    public EventResponse report(@Valid @RequestBody EventRequest req) {
        return service.report(req);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(SecurityRoles.WRITE_BENEFITS)
    public EventResponse approve(@PathVariable UUID id, @RequestBody(required = false) ReviewRequest req) {
        return service.review(id, true, req == null ? null : req.reviewNotes());
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize(SecurityRoles.WRITE_BENEFITS)
    public EventResponse reject(@PathVariable UUID id, @RequestBody(required = false) ReviewRequest req) {
        return service.review(id, false, req == null ? null : req.reviewNotes());
    }
}
