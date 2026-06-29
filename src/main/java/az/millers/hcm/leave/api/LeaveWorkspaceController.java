package az.millers.hcm.leave.api;

import java.time.LocalDate;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.leave.api.dto.LeaveWorkspaceStats;
import az.millers.hcm.leave.service.LeaveWorkspaceService;
import az.millers.hcm.security.SecurityRoles;

@RestController
@RequestMapping("/api/leave/workspace")
public class LeaveWorkspaceController {

    private final LeaveWorkspaceService service;

    public LeaveWorkspaceController(LeaveWorkspaceService service) {
        this.service = service;
    }

    @GetMapping("/stats")
    @PreAuthorize(SecurityRoles.READ_HR)
    public LeaveWorkspaceStats stats(@RequestParam(defaultValue = "0") int year) {
        int resolvedYear = year > 0 ? year : LocalDate.now().getYear();
        return service.stats(resolvedYear);
    }
}
