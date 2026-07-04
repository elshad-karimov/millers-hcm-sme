package az.millers.hcm.attendance.api;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import az.millers.hcm.attendance.api.dto.PeriodDtos.PeriodLockRequest;
import az.millers.hcm.attendance.api.dto.PeriodDtos.PeriodResponse;
import az.millers.hcm.attendance.service.AttendancePeriodService;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.SecurityRoles;

/**
 * M330: Attendance period lock controller.
 */
@RestController
@RequestMapping("/api/attendance/periods")
public class AttendancePeriodController {

    private final AttendancePeriodService service;
    private final CurrentRequest currentRequest;

    public AttendancePeriodController(AttendancePeriodService service, CurrentRequest currentRequest) {
        this.service = service;
        this.currentRequest = currentRequest;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<PeriodResponse> list() {
        return service.list();
    }

    @GetMapping("/{year}/{month}")
    @PreAuthorize(SecurityRoles.READ_HR)
    public PeriodResponse get(@PathVariable int year, @PathVariable int month) {
        return service.getOrCreate(year, month);
    }

    @PostMapping("/{year}/{month}/lock")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public PeriodResponse lock(@PathVariable int year, @PathVariable int month,
                                @RequestBody PeriodLockRequest request) {
        return service.lock(year, month, request.notes(), currentRequest.username());
    }

    @PostMapping("/{year}/{month}/unlock")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public PeriodResponse unlock(@PathVariable int year, @PathVariable int month) {
        return service.unlock(year, month, currentRequest.username());
    }
}
