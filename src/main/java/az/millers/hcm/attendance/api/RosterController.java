package az.millers.hcm.attendance.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.attendance.api.dto.RosterDtos.AssignRequest;
import az.millers.hcm.attendance.api.dto.RosterDtos.BulkAssignRequest;
import az.millers.hcm.attendance.api.dto.RosterDtos.RosterEntryResponse;
import az.millers.hcm.attendance.api.dto.RosterDtos.RosterGrid;
import az.millers.hcm.attendance.api.dto.RosterDtos.SwapRequest;
import az.millers.hcm.attendance.domain.RosterEntry;
import az.millers.hcm.attendance.domain.Shift;
import az.millers.hcm.attendance.repo.ShiftRepository;
import az.millers.hcm.attendance.service.RosterService;
import az.millers.hcm.security.SecurityRoles;
import az.millers.hcm.selfservice.service.EmployeeContextService;
import jakarta.validation.Valid;

/**
 * Roster REST (M110).
 *
 * <p>HR + managers manage the roster; employees see their own via
 * {@code /me}. Locking and swap operations require HR_WRITE.
 */
@RestController
@RequestMapping("/api/attendance/roster")
public class RosterController {

    private final RosterService service;
    private final ShiftRepository shifts;
    private final EmployeeContextService context;

    public RosterController(RosterService service,
                            ShiftRepository shifts,
                            EmployeeContextService context) {
        this.service = service;
        this.shifts = shifts;
        this.context = context;
    }

    @GetMapping("/grid")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public RosterGrid grid(@RequestParam List<UUID> employeeIds,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.grid(employeeIds, from, to);
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<RosterEntryResponse> forEmployee(
            @PathVariable UUID employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.forEmployee(employeeId, from, to);
    }

    /** Employee's own roster — any authenticated employee can read. */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public List<RosterEntryResponse> mine(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        UUID empId = context.currentEmployee().getId();
        return service.forEmployee(empId, from, to);
    }

    @PostMapping("/assign")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public RosterEntryResponse assign(@Valid @RequestBody AssignRequest req) {
        RosterEntry saved = service.assign(req);
        Shift s = shifts.findById(saved.getShiftId()).orElse(null);
        return RosterEntryResponse.from(saved, s, null);
    }

    @PostMapping("/bulk-assign")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public List<RosterEntryResponse> bulkAssign(@Valid @RequestBody BulkAssignRequest req) {
        return service.bulkAssign(req).stream()
                .map(e -> RosterEntryResponse.from(
                        e, shifts.findById(e.getShiftId()).orElse(null), null))
                .toList();
    }

    @PostMapping("/{id}/lock")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public RosterEntryResponse lock(@PathVariable UUID id) {
        RosterEntry saved = service.lock(id);
        Shift s = shifts.findById(saved.getShiftId()).orElse(null);
        return RosterEntryResponse.from(saved, s, null);
    }

    @PostMapping("/swap")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public List<RosterEntryResponse> swap(@Valid @RequestBody SwapRequest req) {
        return service.swap(req).stream()
                .map(e -> RosterEntryResponse.from(
                        e, shifts.findById(e.getShiftId()).orElse(null), null))
                .toList();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public void remove(@PathVariable UUID id) {
        service.remove(id);
    }
}
