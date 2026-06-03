package az.millers.hcm.performance.api;

import az.millers.hcm.security.SecurityRoles;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.performance.api.dto.GoalProgressRequest;
import az.millers.hcm.performance.api.dto.GoalRatingRequest;
import az.millers.hcm.performance.api.dto.GoalRequest;
import az.millers.hcm.performance.api.dto.GoalResponse;
import az.millers.hcm.performance.service.GoalService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/performance/goals")
public class GoalController {

    private final GoalService service;

    public GoalController(GoalService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<GoalResponse> list(@RequestParam UUID cycleId,
                                    @RequestParam(required = false) UUID employeeId) {
        var rows = employeeId == null
                ? service.listForCycle(cycleId)
                : service.listForEmployee(cycleId, employeeId);
        return rows.stream().map(GoalResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public GoalResponse get(@PathVariable UUID id) {
        return GoalResponse.from(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_PLUS_MANAGERS)
    public GoalResponse create(@Valid @RequestBody GoalRequest req) {
        return GoalResponse.from(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_PLUS_MANAGERS)
    public GoalResponse update(@PathVariable UUID id, @Valid @RequestBody GoalRequest req) {
        return GoalResponse.from(service.update(id, req));
    }

    @PostMapping("/{id}/progress")
    @PreAuthorize("isAuthenticated()")
    public GoalResponse progress(@PathVariable UUID id,
                                  @Valid @RequestBody GoalProgressRequest req) {
        return GoalResponse.from(service.updateProgress(id, req));
    }

    @PostMapping("/{id}/rate")
    @PreAuthorize(SecurityRoles.WRITE_HR_PLUS_MANAGERS)
    public GoalResponse rate(@PathVariable UUID id, @Valid @RequestBody GoalRatingRequest req) {
        return GoalResponse.from(service.rate(id, req));
    }
}
