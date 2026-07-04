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

import az.millers.hcm.performance.api.dto.ReviewCycleRequest;
import az.millers.hcm.performance.api.dto.ReviewCycleResponse;
import az.millers.hcm.performance.domain.CycleStatus;
import az.millers.hcm.performance.service.ReviewCycleService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/performance/cycles")
public class ReviewCycleController {

    private final ReviewCycleService service;

    public ReviewCycleController(ReviewCycleService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<ReviewCycleResponse> list(@RequestParam(required = false) CycleStatus status) {
        return service.list(status).stream().map(ReviewCycleResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ReviewCycleResponse get(@PathVariable UUID id) {
        return ReviewCycleResponse.from(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public ReviewCycleResponse create(@Valid @RequestBody ReviewCycleRequest req) {
        return ReviewCycleResponse.from(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public ReviewCycleResponse update(@PathVariable UUID id,
                                       @Valid @RequestBody ReviewCycleRequest req) {
        return ReviewCycleResponse.from(service.update(id, req));
    }

    @PostMapping("/{id}/status/{status}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public ReviewCycleResponse changeStatus(@PathVariable UUID id,
                                              @PathVariable CycleStatus status,
                                              @RequestParam(required = false) String reason) {
        return ReviewCycleResponse.from(service.changeStatus(id, status, reason));
    }
}
