package az.millers.hcm.staffing.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.security.SecurityRoles;
import az.millers.hcm.staffing.service.CareerPathService;
import az.millers.hcm.staffing.service.CareerPathService.CareerPathRequest;
import az.millers.hcm.staffing.service.CareerPathService.CareerPathResponse;

/**
 * HCM_16 M416 — career paths (PRD §16.6).
 * Transparent to all employees (read); HR-only write.
 */
@RestController
@RequestMapping("/api/staffing/career-paths")
public class CareerPathController {

    private final CareerPathService service;

    public CareerPathController(CareerPathService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<CareerPathResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public CareerPathResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public CareerPathResponse create(@RequestBody CareerPathRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public CareerPathResponse update(@PathVariable UUID id,
                                      @RequestBody CareerPathRequest req) {
        return service.update(id, req);
    }
}
