package az.millers.hcm.attendance.api;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import az.millers.hcm.attendance.api.dto.WorkspaceDtos.AttendanceWorkspaceSummary;
import az.millers.hcm.attendance.service.AttendanceWorkspaceService;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.SecurityRoles;

/**
 * M335: Attendance workspace controller.
 */
@RestController
@RequestMapping("/api/attendance/workspace")
public class AttendanceWorkspaceController {

    private final AttendanceWorkspaceService service;
    private final CurrentRequest currentRequest;

    public AttendanceWorkspaceController(AttendanceWorkspaceService service,
                                          CurrentRequest currentRequest) {
        this.service = service;
        this.currentRequest = currentRequest;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public AttendanceWorkspaceSummary getWorkspace(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) UUID departmentId) {
        return service.getWorkspace(date, departmentId);
    }
}
