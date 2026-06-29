package az.millers.hcm.leave.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.leave.domain.LeaveEncashment;
import az.millers.hcm.leave.service.LeaveEncashmentService;
import az.millers.hcm.security.SecurityRoles;

@RestController
@RequestMapping("/api/leave/encashments")
public class LeaveEncashmentController {

    private final LeaveEncashmentService service;

    public LeaveEncashmentController(LeaveEncashmentService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public ResponseEntity<List<LeaveEncashment>> list(@RequestParam UUID employeeId) {
        return ResponseEntity.ok(service.listForEmployee(employeeId));
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public ResponseEntity<LeaveEncashment> encash(
            @RequestParam UUID employeeId,
            @RequestParam UUID leaveTypeId,
            @RequestParam int year,
            @RequestParam BigDecimal days,
            @RequestParam int payrollYear,
            @RequestParam int payrollMonth,
            @RequestParam(required = false) String notes) {
        return ResponseEntity.ok(
                service.encash(employeeId, leaveTypeId, year, days, payrollYear, payrollMonth, notes));
    }

    @PostMapping("/{id}/reverse")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public ResponseEntity<LeaveEncashment> reverse(@PathVariable UUID id) {
        return ResponseEntity.ok(service.reverse(id));
    }
}
