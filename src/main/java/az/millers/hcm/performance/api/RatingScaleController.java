package az.millers.hcm.performance.api;

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

import az.millers.hcm.performance.api.dto.RatingScaleDtos.ScaleRequest;
import az.millers.hcm.performance.api.dto.RatingScaleDtos.ScaleResponse;
import az.millers.hcm.performance.service.RatingScaleService;
import az.millers.hcm.security.SecurityRoles;
import jakarta.validation.Valid;

/**
 * Rating scale master API (HCM_12 M388). Reads open to authenticated users (scales
 * drive review forms); writes HR-admin only (changing a scale affects rating outcomes).
 */
@RestController
@RequestMapping("/api/performance/rating-scales")
public class RatingScaleController {

    private final RatingScaleService service;

    public RatingScaleController(RatingScaleService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<ScaleResponse> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return service.list(activeOnly);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ScaleResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public ScaleResponse create(@Valid @RequestBody ScaleRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public ScaleResponse update(@PathVariable UUID id, @Valid @RequestBody ScaleRequest req) {
        return service.update(id, req);
    }
}
