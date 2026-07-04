package az.millers.hcm.attendance.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.attendance.api.dto.ShiftDtos.ShiftRequest;
import az.millers.hcm.attendance.api.dto.ShiftDtos.ShiftResponse;
import az.millers.hcm.attendance.service.ShiftService;
import az.millers.hcm.security.SecurityRoles;
import jakarta.validation.Valid;

/**
 * Shift catalog REST (M110). HR_PLUS_MANAGERS read so managers can pick
 * shifts when rostering their team; HR_WRITE for catalog edits, HR_ADMIN
 * for archive (destructive-ish — orphans existing roster pointers).
 */
@RestController
@RequestMapping("/api/attendance/shifts")
public class ShiftController {

    private final ShiftService service;

    public ShiftController(ShiftService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<ShiftResponse> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return service.list(activeOnly).stream().map(ShiftResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public ShiftResponse get(@PathVariable UUID id) {
        return ShiftResponse.from(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public ShiftResponse create(@Valid @RequestBody ShiftRequest req) {
        return ShiftResponse.from(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public ShiftResponse update(@PathVariable UUID id, @Valid @RequestBody ShiftRequest req) {
        return ShiftResponse.from(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public ShiftResponse archive(@PathVariable UUID id) {
        return ShiftResponse.from(service.archive(id));
    }
}
