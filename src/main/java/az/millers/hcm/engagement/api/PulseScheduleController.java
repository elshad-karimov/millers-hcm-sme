package az.millers.hcm.engagement.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import az.millers.hcm.engagement.domain.PulseSchedule;
import az.millers.hcm.engagement.service.PulseScheduleService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M477 — Pulse schedule CRUD (HR_ADMIN only).
 */
@RestController
@RequestMapping("/api/engagement/pulse-schedules")
public class PulseScheduleController {

    private final PulseScheduleService service;

    public PulseScheduleController(PulseScheduleService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<PulseSchedule> listAll(@RequestParam(required = false, defaultValue = "true") Boolean activeOnly) {
        return activeOnly ? service.listActive() : service.listAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_HR)
    public PulseSchedule get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public PulseSchedule create(@RequestBody PulseSchedule schedule) {
        return service.create(schedule);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public PulseSchedule update(@PathVariable UUID id, @RequestBody PulseSchedule schedule) {
        return service.update(id, schedule);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
