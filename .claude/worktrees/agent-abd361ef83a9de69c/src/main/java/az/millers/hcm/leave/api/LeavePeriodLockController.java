package az.millers.hcm.leave.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.leave.api.dto.LeavePeriodLockRequest;
import az.millers.hcm.leave.api.dto.LeavePeriodLockResponse;
import az.millers.hcm.leave.service.LeavePeriodLockService;
import az.millers.hcm.security.SecurityRoles;

@RestController
@RequestMapping("/api/leave/period-locks")
public class LeavePeriodLockController {

    private final LeavePeriodLockService service;

    public LeavePeriodLockController(LeavePeriodLockService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<LeavePeriodLockResponse> list() {
        return service.listAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_HR)
    public LeavePeriodLockResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public LeavePeriodLockResponse create(@Valid @RequestBody LeavePeriodLockRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public LeavePeriodLockResponse update(@PathVariable UUID id,
                                          @Valid @RequestBody LeavePeriodLockRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public void deactivate(@PathVariable UUID id) {
        service.deactivate(id);
    }
}
