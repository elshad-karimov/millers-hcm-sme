package az.millers.hcm.attendance.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.attendance.api.dto.AttendancePolicyDtos.AttendancePolicyRequest;
import az.millers.hcm.attendance.api.dto.AttendancePolicyDtos.AttendancePolicyResponse;
import az.millers.hcm.attendance.service.AttendancePolicyService;
import az.millers.hcm.security.SecurityRoles;

@RestController
@RequestMapping("/api/attendance/policies")
public class AttendancePolicyController {

    private final AttendancePolicyService service;

    public AttendancePolicyController(AttendancePolicyService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<AttendancePolicyResponse> list() {
        return service.list().stream().map(AttendancePolicyResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_HR)
    public AttendancePolicyResponse get(@PathVariable UUID id) {
        return AttendancePolicyResponse.from(service.get(id));
    }

    @GetMapping("/resolve")
    @PreAuthorize(SecurityRoles.READ_HR)
    public ResponseEntity<AttendancePolicyResponse> resolve(@RequestParam UUID employeeId) {
        return service.findForEmployee(employeeId)
                .map(p -> ResponseEntity.ok(AttendancePolicyResponse.from(p)))
                .orElse(ResponseEntity.noContent().build());
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public AttendancePolicyResponse create(@RequestBody AttendancePolicyRequest req) {
        return AttendancePolicyResponse.from(service.create(req.toEntity()));
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public AttendancePolicyResponse update(@PathVariable UUID id, @RequestBody AttendancePolicyRequest req) {
        return AttendancePolicyResponse.from(service.update(id, req.toEntity()));
    }

    @DeleteMapping("/{id}/deactivate")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        service.setActive(id, false);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public ResponseEntity<Void> activate(@PathVariable UUID id) {
        service.setActive(id, true);
        return ResponseEntity.noContent().build();
    }
}
