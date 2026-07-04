package az.millers.hcm.selfservice.api;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.selfservice.api.dto.AnnouncementDto;
import az.millers.hcm.selfservice.service.AnnouncementService;
import az.millers.hcm.selfservice.service.EmployeeContextService;

/**
 * M430 — ESS: employee views active announcements.
 */
@RestController
@RequestMapping("/api/self/announcements")
public class SelfAnnouncementController {

    private final AnnouncementService service;
    private final EmployeeContextService employeeContext;

    public SelfAnnouncementController(AnnouncementService service,
                                       EmployeeContextService employeeContext) {
        this.service = service;
        this.employeeContext = employeeContext;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'MANAGER', 'HR_ADMIN', 'HR_SPECIALIST')")
    public List<AnnouncementDto> active() {
        Employee emp = employeeContext.currentEmployee();
        return service.activeFor(emp).stream()
                .map(AnnouncementDto::from)
                .toList();
    }
}
