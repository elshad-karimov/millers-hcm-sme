package az.millers.hcm.performance.api;

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

import az.millers.hcm.performance.domain.ContinuousFeedback;
import az.millers.hcm.performance.domain.PerfCheckIn;
import az.millers.hcm.performance.service.ContinuousFeedbackService;

/** Continuous feedback + check-ins (HCM_12 M400, PRD §22). Scope-guarded in the service. */
@RestController
@RequestMapping("/api/performance/continuous")
public class ContinuousFeedbackController {

    private final ContinuousFeedbackService service;

    public ContinuousFeedbackController(ContinuousFeedbackService service) {
        this.service = service;
    }

    @GetMapping("/feedback")
    @PreAuthorize("isAuthenticated()")
    public List<ContinuousFeedback> feedback(@RequestParam UUID employeeId) {
        return service.listFeedback(employeeId);
    }

    @PostMapping("/feedback")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public ContinuousFeedback give(@RequestBody ContinuousFeedbackService.FeedbackNoteRequest req) {
        return service.give(req);
    }

    @GetMapping("/check-ins")
    @PreAuthorize("isAuthenticated()")
    public List<PerfCheckIn> checkIns(@RequestParam UUID employeeId) {
        return service.listCheckIns(employeeId);
    }

    @PostMapping("/check-ins")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public PerfCheckIn createCheckIn(@RequestBody ContinuousFeedbackService.CheckInRequest req) {
        return service.createCheckIn(req);
    }

    @PostMapping("/check-ins/{id}/acknowledge")
    @PreAuthorize("isAuthenticated()")
    public PerfCheckIn acknowledge(@PathVariable UUID id) {
        return service.acknowledge(id);
    }
}
