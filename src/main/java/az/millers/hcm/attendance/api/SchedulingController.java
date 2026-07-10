package az.millers.hcm.attendance.api;

import az.millers.hcm.attendance.api.dto.OpenShiftRequest;
import az.millers.hcm.attendance.api.dto.SwapRequestDto;
import az.millers.hcm.attendance.domain.OpenShift;
import az.millers.hcm.attendance.domain.ShiftSwapRequest;
import az.millers.hcm.attendance.service.SchedulingService;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.SecurityRoles;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * M482: Open shifts + shift swap endpoints.
 */
@RestController
@RequestMapping("/api/attendance/scheduling")
public class SchedulingController {

    private final SchedulingService service;
    private final CurrentRequest currentRequest;

    public SchedulingController(SchedulingService service, CurrentRequest currentRequest) {
        this.service = service;
        this.currentRequest = currentRequest;
    }

    // ───────────────────────────── Open Shifts ─────────────────────────────

    @GetMapping("/open-shifts")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<OpenShift> listOpenShifts(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return service.listOpenShifts(from, to);
    }

    @GetMapping("/open-shifts/{id}")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public OpenShift getOpenShift(@PathVariable UUID id) {
        return service.getOpenShift(id);
    }

    @PostMapping("/open-shifts")
    @PreAuthorize(SecurityRoles.WRITE_HR_PLUS_MANAGERS)
    public OpenShift createOpenShift(@Valid @RequestBody OpenShiftRequest req) {
        OpenShift shift = new OpenShift();
        shift.setShiftId(req.shiftId());
        shift.setShiftDate(req.shiftDate());
        shift.setOrgUnitId(req.orgUnitId());
        shift.setSlots(req.slots());
        shift.setNotes(req.notes());
        return service.createOpenShift(shift);
    }

    @PutMapping("/open-shifts/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_PLUS_MANAGERS)
    public OpenShift updateOpenShift(@PathVariable UUID id, @Valid @RequestBody OpenShiftRequest req) {
        OpenShift updated = new OpenShift();
        updated.setSlots(req.slots());
        updated.setNotes(req.notes());
        return service.updateOpenShift(id, updated);
    }

    @PostMapping("/open-shifts/{id}/claim")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public void claimOpenShift(@PathVariable UUID id, @RequestParam UUID employeeId) {
        service.claimOpenShift(id, employeeId);
    }

    // ───────────────────────────── Shift Swaps ─────────────────────────────

    @GetMapping("/swap-requests")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<ShiftSwapRequest> listSwapRequests(@RequestParam(required = false) String status) {
        return service.listSwapRequests(status);
    }

    @GetMapping("/swap-requests/{id}")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public ShiftSwapRequest getSwapRequest(@PathVariable UUID id) {
        return service.getSwapRequest(id);
    }

    @PostMapping("/swap-requests")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public ShiftSwapRequest createSwapRequest(@Valid @RequestBody SwapRequestDto req) {
        return service.createSwapRequest(req.rosterEntryId(), req.fromEmployeeId(), req.toEmployeeId(), req.notes());
    }

    @PostMapping("/swap-requests/{id}/approve")
    @PreAuthorize(SecurityRoles.WRITE_HR_PLUS_MANAGERS)
    public void approveSwapRequest(@PathVariable UUID id) {
        service.approveSwapRequest(id, currentRequest.username());
    }

    @PostMapping("/swap-requests/{id}/reject")
    @PreAuthorize(SecurityRoles.WRITE_HR_PLUS_MANAGERS)
    public void rejectSwapRequest(@PathVariable UUID id, @RequestParam String reason) {
        service.rejectSwapRequest(id, reason);
    }
}
