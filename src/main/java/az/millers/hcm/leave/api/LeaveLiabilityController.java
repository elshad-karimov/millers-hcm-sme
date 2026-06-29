package az.millers.hcm.leave.api;

import java.time.LocalDate;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.leave.api.dto.LeaveLiabilityReport;
import az.millers.hcm.leave.service.LeaveLiabilityService;
import az.millers.hcm.security.SecurityRoles;

@RestController
@RequestMapping("/api/leave/reports")
public class LeaveLiabilityController {

    private final LeaveLiabilityService service;

    public LeaveLiabilityController(LeaveLiabilityService service) {
        this.service = service;
    }

    @GetMapping("/liability")
    @PreAuthorize(SecurityRoles.READ_HR)
    public LeaveLiabilityReport liability(
            @RequestParam(defaultValue = "0") int year,
            @RequestParam(defaultValue = "22") int workingDaysPerMonth) {
        int resolvedYear = year > 0 ? year : LocalDate.now().getYear();
        return service.generate(resolvedYear, workingDaysPerMonth);
    }
}
