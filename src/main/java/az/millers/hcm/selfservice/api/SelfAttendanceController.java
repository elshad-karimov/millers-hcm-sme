package az.millers.hcm.selfservice.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.selfservice.api.dto.EmployeeDirectoryResponse;
import az.millers.hcm.selfservice.api.dto.GeofenceConfigResponse;
import az.millers.hcm.selfservice.api.dto.MobilePunchRequest;
import az.millers.hcm.selfservice.api.dto.MobilePunchResponse;
import az.millers.hcm.selfservice.service.EmployeeDirectoryService;
import az.millers.hcm.selfservice.service.MobileAttendanceService;
import jakarta.validation.Valid;

/**
 * M495 — Mobile attendance punch.
 * M497 — Geofence config.
 * M504 — Employee directory.
 */
@RestController
@RequestMapping("/api/self")
@PreAuthorize("isAuthenticated()")
public class SelfAttendanceController {

    private final MobileAttendanceService attendanceService;
    private final EmployeeDirectoryService directoryService;

    public SelfAttendanceController(MobileAttendanceService attendanceService,
                                    EmployeeDirectoryService directoryService) {
        this.attendanceService = attendanceService;
        this.directoryService = directoryService;
    }

    /**
     * M495 — Records a mobile attendance punch.
     * IDOR-safe: only the current employee can punch (via EmployeeContextService).
     */
    @PostMapping("/attendance/punch")
    @ResponseStatus(HttpStatus.CREATED)
    public MobilePunchResponse punch(@Valid @RequestBody MobilePunchRequest req) {
        return attendanceService.punch(req);
    }

    /**
     * M497 — Returns geofence locations for the current employee.
     * Derives from employee's work_location GPS coords + tenant settings.
     */
    @GetMapping("/attendance/geofences")
    public GeofenceConfigResponse getGeofences() {
        return attendanceService.getGeofenceConfig();
    }

    /**
     * M504 — Employee directory search.
     * Returns PUBLIC fields only (no salary, national_id, bank, DOB, home address).
     */
    @GetMapping("/directory")
    public List<EmployeeDirectoryResponse> searchDirectory(
            @RequestParam(required = false, defaultValue = "") String q) {
        return directoryService.search(q);
    }
}
