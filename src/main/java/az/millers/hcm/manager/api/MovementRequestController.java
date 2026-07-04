package az.millers.hcm.manager.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.lifecycle.domain.EmployeeMovementRequest;
import az.millers.hcm.lifecycle.service.EmployeeMovementService;
import az.millers.hcm.manager.api.dto.CreateMovementRequestDto;
import az.millers.hcm.manager.api.dto.MovementRequestDto;
import az.millers.hcm.security.CurrentRequest;

/**
 * M432 — Employee movement requests (manager + HR).
 */
@RestController
@RequestMapping("/api/manager/movements")
public class MovementRequestController {

    private final EmployeeMovementService service;
    private final CurrentRequest currentRequest;

    public MovementRequestController(EmployeeMovementService service,
                                      CurrentRequest currentRequest) {
        this.service = service;
        this.currentRequest = currentRequest;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('DEPARTMENT_MANAGER', 'HR_ADMIN')")
    public List<MovementRequestDto> list() {
        boolean isHR = currentRequest.hasRole("HR_ADMIN");
        return service.list().stream()
                .map(req -> MovementRequestDto.from(req, isHR))
                .toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('DEPARTMENT_MANAGER', 'HR_ADMIN')")
    public MovementRequestDto get(@PathVariable UUID id) {
        boolean isHR = currentRequest.hasRole("HR_ADMIN");
        return MovementRequestDto.from(service.get(id), isHR);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('DEPARTMENT_MANAGER', 'HR_ADMIN')")
    public MovementRequestDto create(@RequestBody CreateMovementRequestDto dto) {
        boolean isHR = currentRequest.hasRole("HR_ADMIN");
        EmployeeMovementRequest req = service.create(
                dto.employeeId(),
                dto.movementType(),
                dto.proposedPositionId(),
                dto.proposedOrgUnitId(),
                dto.proposedGrade(),
                dto.proposedSalary(),
                dto.effectiveDate(),
                dto.justification()
        );
        // Auto-submit
        req = service.submit(req.getId());
        return MovementRequestDto.from(req, isHR);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('DEPARTMENT_MANAGER', 'HR_ADMIN')")
    public MovementRequestDto cancel(@PathVariable UUID id) {
        boolean isHR = currentRequest.hasRole("HR_ADMIN");
        return MovementRequestDto.from(service.cancel(id), isHR);
    }
}
