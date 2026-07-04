package az.millers.hcm.attendance.api;

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

import az.millers.hcm.attendance.api.dto.ScheduleAssignmentRequest;
import az.millers.hcm.attendance.api.dto.ScheduleAssignmentResponse;
import az.millers.hcm.attendance.api.dto.WorkScheduleRequest;
import az.millers.hcm.attendance.api.dto.WorkScheduleResponse;
import az.millers.hcm.attendance.service.WorkScheduleService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/attendance")
public class WorkScheduleController {

    private final WorkScheduleService service;

    public WorkScheduleController(WorkScheduleService service) {
        this.service = service;
    }

    @GetMapping("/schedules")
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<WorkScheduleResponse> list() {
        return service.list().stream().map(WorkScheduleResponse::from).toList();
    }

    @GetMapping("/schedules/{id}")
    @PreAuthorize(SecurityRoles.READ_HR)
    public WorkScheduleResponse get(@PathVariable UUID id) {
        return WorkScheduleResponse.from(service.get(id));
    }

    @PostMapping("/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST')")
    public WorkScheduleResponse create(@Valid @RequestBody WorkScheduleRequest req) {
        return WorkScheduleResponse.from(service.create(req));
    }

    @PutMapping("/schedules/{id}")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST')")
    public WorkScheduleResponse update(@PathVariable UUID id,
                                        @Valid @RequestBody WorkScheduleRequest req) {
        return WorkScheduleResponse.from(service.update(id, req));
    }

    // ---------- Assignments ----------

    @GetMapping("/assignments")
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<ScheduleAssignmentResponse> assignmentsFor(@RequestParam UUID employeeId) {
        return service.assignmentsFor(employeeId).stream()
                .map(ScheduleAssignmentResponse::from).toList();
    }

    @PostMapping("/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST')")
    public ScheduleAssignmentResponse assign(@Valid @RequestBody ScheduleAssignmentRequest req) {
        return ScheduleAssignmentResponse.from(service.assign(req));
    }
}
