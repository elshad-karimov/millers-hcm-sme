package az.millers.hcm.staffing.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.security.SecurityRoles;
import az.millers.hcm.staffing.api.dto.PositionOccupancyDtos.EndOccupancyRequest;
import az.millers.hcm.staffing.api.dto.PositionOccupancyDtos.OccupancyRequest;
import az.millers.hcm.staffing.api.dto.PositionOccupancyDtos.OccupancyResponse;
import az.millers.hcm.staffing.service.PositionOccupancyService;
import jakarta.validation.Valid;

/** M246 — REST surface for position occupancy. */
@RestController
@RequestMapping("/api/position-occupancies")
public class PositionOccupancyController {

    private final PositionOccupancyService service;

    public PositionOccupancyController(PositionOccupancyService service) {
        this.service = service;
    }

    @GetMapping("/by-position/{positionId}")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','RECRUITER','FINANCE_USER','MANAGER','DEPARTMENT_MANAGER')")
    public List<OccupancyResponse> byPosition(@PathVariable UUID positionId) {
        return service.forPosition(positionId).stream().map(OccupancyResponse::from).toList();
    }

    @GetMapping("/by-employee/{employeeId}")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','MANAGER','DEPARTMENT_MANAGER')")
    public List<OccupancyResponse> byEmployee(@PathVariable UUID employeeId) {
        return service.forEmployee(employeeId).stream().map(OccupancyResponse::from).toList();
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public OccupancyResponse create(@Valid @RequestBody OccupancyRequest req) {
        return OccupancyResponse.from(service.create(req.toEntity()));
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public OccupancyResponse update(@PathVariable UUID id, @Valid @RequestBody OccupancyRequest req) {
        return OccupancyResponse.from(service.update(id, req.toEntity()));
    }

    @PostMapping("/{id}/end")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public OccupancyResponse end(@PathVariable UUID id, @Valid @RequestBody EndOccupancyRequest req) {
        return OccupancyResponse.from(
                service.end(id, req.endDate(), req.reason(), req.notes()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
