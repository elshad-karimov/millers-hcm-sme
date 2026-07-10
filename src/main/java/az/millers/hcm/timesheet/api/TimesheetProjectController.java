package az.millers.hcm.timesheet.api;

import az.millers.hcm.security.SecurityRoles;
import az.millers.hcm.timesheet.api.dto.TimesheetProjectRequest;
import az.millers.hcm.timesheet.domain.TimesheetProject;
import az.millers.hcm.timesheet.service.TimesheetProjectService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * M484: Timesheet project endpoints.
 */
@RestController
@RequestMapping("/api/timesheet/projects")
public class TimesheetProjectController {

    private final TimesheetProjectService service;

    public TimesheetProjectController(TimesheetProjectService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<TimesheetProject> listProjects(@RequestParam(required = false, defaultValue = "true") boolean activeOnly) {
        return service.listProjects(activeOnly);
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public TimesheetProject getProject(@PathVariable UUID id) {
        return service.getProject(id);
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public TimesheetProject createProject(@Valid @RequestBody TimesheetProjectRequest req) {
        TimesheetProject project = new TimesheetProject();
        project.setCode(req.code());
        project.setName(req.name());
        project.setDescription(req.description());
        project.setBillingRate(req.billingRate());
        project.setActive(req.active() != null ? req.active() : true);
        return service.createProject(project);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public TimesheetProject updateProject(@PathVariable UUID id, @Valid @RequestBody TimesheetProjectRequest req) {
        TimesheetProject updated = new TimesheetProject();
        updated.setName(req.name());
        updated.setDescription(req.description());
        updated.setBillingRate(req.billingRate());
        updated.setActive(req.active() != null ? req.active() : true);
        return service.updateProject(id, updated);
    }
}
