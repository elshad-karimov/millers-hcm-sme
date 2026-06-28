package az.millers.hcm.attendance.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import az.millers.hcm.attendance.api.dto.ExceptionDtos.AttendanceExceptionResponse;
import az.millers.hcm.attendance.api.dto.ExceptionDtos.ExceptionConfigRequest;
import az.millers.hcm.attendance.api.dto.ExceptionDtos.ExceptionConfigResponse;
import az.millers.hcm.attendance.api.dto.ExceptionDtos.ExceptionDecision;
import az.millers.hcm.attendance.service.AttendanceExceptionService;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.SecurityRoles;

/**
 * M332: Attendance exception controller.
 */
@RestController
@RequestMapping("/api/attendance/exceptions")
public class AttendanceExceptionController {

    private final AttendanceExceptionService service;
    private final CurrentRequest currentRequest;

    public AttendanceExceptionController(AttendanceExceptionService service,
                                          CurrentRequest currentRequest) {
        this.service = service;
        this.currentRequest = currentRequest;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<AttendanceExceptionResponse> list(@RequestParam(required = false) String status) {
        return service.list(status);
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<AttendanceExceptionResponse> listByEmployee(@PathVariable UUID employeeId) {
        return service.listByEmployee(employeeId);
    }

    @GetMapping("/configs")
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<ExceptionConfigResponse> getConfigs() {
        return service.getConfigs();
    }

    @PutMapping("/configs/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public ExceptionConfigResponse updateConfig(@PathVariable UUID id,
                                                  @RequestBody ExceptionConfigRequest request) {
        return service.updateConfig(id, request);
    }

    @PostMapping("/{id}/acknowledge")
    @PreAuthorize(SecurityRoles.WRITE_HR_PLUS_MANAGERS)
    public AttendanceExceptionResponse acknowledge(@PathVariable UUID id) {
        return service.acknowledge(id, currentRequest.username());
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public AttendanceExceptionResponse resolve(@PathVariable UUID id,
                                                 @RequestBody ExceptionDecision decision) {
        return service.resolve(id, currentRequest.username(), decision.notes());
    }
}
