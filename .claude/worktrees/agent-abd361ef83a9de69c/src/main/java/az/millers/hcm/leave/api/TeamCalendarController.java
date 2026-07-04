package az.millers.hcm.leave.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.leave.api.dto.TeamCalendarDtos.TeamCalendarResponse;
import az.millers.hcm.leave.service.TeamCalendarService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M131 — team time-off calendar. ABAC scoped via
 * {@link az.millers.hcm.security.scope.AccessScopeService}: HR / admin
 * see everyone in {@code orgUnitId}'s sub-tree; managers see the
 * intersection of their reporting chain with the org unit.
 */
@RestController
@RequestMapping("/api/leave/team-calendar")
public class TeamCalendarController {

    private final TeamCalendarService service;

    public TeamCalendarController(TeamCalendarService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public TeamCalendarResponse calendar(
            @RequestParam(required = false) UUID orgUnitId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate windowStart,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate windowEnd,
            @RequestParam(required = false) BigDecimal thresholdPercent) {
        return service.teamCalendar(orgUnitId, windowStart, windowEnd, thresholdPercent);
    }
}
