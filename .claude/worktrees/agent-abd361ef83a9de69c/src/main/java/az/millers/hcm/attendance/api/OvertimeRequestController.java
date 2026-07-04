package az.millers.hcm.attendance.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import az.millers.hcm.attendance.api.dto.OvertimeDtos.OvertimeDecision;
import az.millers.hcm.attendance.api.dto.OvertimeDtos.OvertimeRequestDto;
import az.millers.hcm.attendance.api.dto.OvertimeDtos.OvertimeResponse;
import az.millers.hcm.attendance.service.OvertimeRequestService;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.SecurityRoles;

/**
 * M329: Overtime request controller.
 */
@RestController
@RequestMapping("/api/attendance/overtime-requests")
public class OvertimeRequestController {

    private final OvertimeRequestService service;
    private final CurrentRequest currentRequest;

    public OvertimeRequestController(OvertimeRequestService service, CurrentRequest currentRequest) {
        this.service = service;
        this.currentRequest = currentRequest;
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_HR_PLUS_MANAGERS)
    public OvertimeResponse submit(@RequestBody OvertimeRequestDto request) {
        return service.submit(request, currentRequest.username());
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<OvertimeResponse> list() {
        return service.list();
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<OvertimeResponse> listByEmployee(@PathVariable UUID employeeId) {
        return service.listByEmployee(employeeId);
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public OvertimeResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public OvertimeResponse approve(@PathVariable UUID id, @RequestBody OvertimeDecision decision) {
        return service.decide(id, "APPROVED", decision.comment(), currentRequest.username());
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public OvertimeResponse reject(@PathVariable UUID id, @RequestBody OvertimeDecision decision) {
        return service.decide(id, "REJECTED", decision.comment(), currentRequest.username());
    }
}
