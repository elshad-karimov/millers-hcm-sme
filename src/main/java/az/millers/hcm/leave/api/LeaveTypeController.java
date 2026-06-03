package az.millers.hcm.leave.api;

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

import az.millers.hcm.leave.api.dto.LeaveTypeRequest;
import az.millers.hcm.leave.api.dto.LeaveTypeResponse;
import az.millers.hcm.leave.service.LeaveTypeService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/leave/types")
public class LeaveTypeController {

    private final LeaveTypeService service;

    public LeaveTypeController(LeaveTypeService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<LeaveTypeResponse> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        var rows = activeOnly ? service.listActive() : service.list();
        return rows.stream().map(LeaveTypeResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public LeaveTypeResponse get(@PathVariable UUID id) {
        return LeaveTypeResponse.from(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public LeaveTypeResponse create(@Valid @RequestBody LeaveTypeRequest req) {
        return LeaveTypeResponse.from(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public LeaveTypeResponse update(@PathVariable UUID id, @Valid @RequestBody LeaveTypeRequest req) {
        return LeaveTypeResponse.from(service.update(id, req));
    }
}
