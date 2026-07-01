package az.millers.hcm.compensation.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.compensation.api.dto.CreateSalaryChangeRequest;
import az.millers.hcm.compensation.api.dto.SalaryChangeRequestDto;
import az.millers.hcm.compensation.domain.SalaryChangeStatus;
import az.millers.hcm.compensation.service.SalaryChangeRequestService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M361 — Salary change request controller.
 * Actual approve/reject happens through the workflow inbox/act endpoints.
 */
@RestController
@RequestMapping("/api/compensation/salary-changes")
public class SalaryChangeRequestController {

    private final SalaryChangeRequestService service;

    public SalaryChangeRequestController(SalaryChangeRequestService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_COMPENSATION)
    public List<SalaryChangeRequestDto> list(
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) SalaryChangeStatus status) {
        return service.list(employeeId, status);
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_COMPENSATION)
    public SalaryChangeRequestDto get(@PathVariable UUID id) {
        return SalaryChangeRequestDto.from(service.get(id));
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_COMPENSATION)
    public SalaryChangeRequestDto create(@RequestBody CreateSalaryChangeRequest req) {
        return SalaryChangeRequestDto.from(service.create(req));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize(SecurityRoles.WRITE_COMPENSATION)
    public SalaryChangeRequestDto submit(@PathVariable UUID id) {
        return SalaryChangeRequestDto.from(service.submit(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(SecurityRoles.WRITE_COMPENSATION)
    public SalaryChangeRequestDto cancel(@PathVariable UUID id) {
        return SalaryChangeRequestDto.from(service.cancel(id));
    }
}
