package az.millers.hcm.attendance.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import az.millers.hcm.attendance.api.dto.CorrectionDtos.CorrectionDecision;
import az.millers.hcm.attendance.api.dto.CorrectionDtos.CorrectionRequest;
import az.millers.hcm.attendance.api.dto.CorrectionDtos.CorrectionResponse;
import az.millers.hcm.attendance.service.AttendanceCorrectionService;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.SecurityRoles;

/**
 * M328: Attendance correction request controller.
 */
@RestController
@RequestMapping("/api/attendance/corrections")
public class AttendanceCorrectionController {

    private final AttendanceCorrectionService service;
    private final CurrentRequest currentRequest;

    public AttendanceCorrectionController(AttendanceCorrectionService service,
                                           CurrentRequest currentRequest) {
        this.service = service;
        this.currentRequest = currentRequest;
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public CorrectionResponse submit(@RequestBody CorrectionRequest request) {
        return service.submit(request, currentRequest.username());
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<CorrectionResponse> list() {
        return service.list();
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<CorrectionResponse> listByEmployee(@PathVariable UUID employeeId) {
        return service.listByEmployee(employeeId);
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public CorrectionResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public CorrectionResponse approve(@PathVariable UUID id, @RequestBody CorrectionDecision decision) {
        return service.decide(id, "APPROVED", decision.comment(), currentRequest.username());
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public CorrectionResponse reject(@PathVariable UUID id, @RequestBody CorrectionDecision decision) {
        return service.decide(id, "REJECTED", decision.comment(), currentRequest.username());
    }
}
