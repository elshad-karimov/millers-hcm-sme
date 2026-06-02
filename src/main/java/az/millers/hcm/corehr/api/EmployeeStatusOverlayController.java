package az.millers.hcm.corehr.api;

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

import az.millers.hcm.corehr.api.dto.StatusOverlayRequest;
import az.millers.hcm.corehr.api.dto.StatusOverlayResponse;
import az.millers.hcm.corehr.service.EmployeeStatusOverlayService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/employees/{employeeId}/status-overlays")
public class EmployeeStatusOverlayController {

    private static final String READ_ROLES =
            "hasAnyRole('HR_ADMIN','HR_SPECIALIST','DEPARTMENT_MANAGER','SYSTEM_ADMIN','AUDITOR')";
    private static final String WRITE_ROLES =
            "hasAnyRole('HR_ADMIN','HR_SPECIALIST','SYSTEM_ADMIN')";

    private final EmployeeStatusOverlayService service;

    public EmployeeStatusOverlayController(EmployeeStatusOverlayService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public List<StatusOverlayResponse> list(@PathVariable UUID employeeId,
                                             @RequestParam(defaultValue = "false") boolean openOnly) {
        return openOnly ? service.openFor(employeeId) : service.listFor(employeeId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE_ROLES)
    public StatusOverlayResponse apply(@PathVariable UUID employeeId,
                                        @Valid @RequestBody StatusOverlayRequest req) {
        return service.apply(employeeId, req);
    }

    @PostMapping("/{overlayId}/close")
    @PreAuthorize(WRITE_ROLES)
    public StatusOverlayResponse close(@PathVariable UUID employeeId,
                                        @PathVariable UUID overlayId,
                                        @RequestParam(required = false)
                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                        LocalDate closeOn) {
        return service.close(overlayId, closeOn);
    }

    @DeleteMapping("/{overlayId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(WRITE_ROLES)
    public void delete(@PathVariable UUID employeeId, @PathVariable UUID overlayId) {
        service.delete(overlayId);
    }
}
