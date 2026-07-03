package az.millers.hcm.compbenefits.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.compbenefits.api.dto.OpenEnrollmentDtos.OpenStatus;
import az.millers.hcm.compbenefits.api.dto.OpenEnrollmentDtos.WindowRequest;
import az.millers.hcm.compbenefits.api.dto.OpenEnrollmentDtos.WindowResponse;
import az.millers.hcm.compbenefits.service.OpenEnrollmentService;
import az.millers.hcm.security.SecurityRoles;
import jakarta.validation.Valid;

/** Open-enrollment window API (HCM_11 M379). Status is readable by any authenticated user. */
@RestController
@RequestMapping("/api/compbenefits/open-enrollment")
public class OpenEnrollmentController {

    private final OpenEnrollmentService service;

    public OpenEnrollmentController(OpenEnrollmentService service) {
        this.service = service;
    }

    @GetMapping("/windows")
    @PreAuthorize(SecurityRoles.READ_BENEFITS)
    public List<WindowResponse> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return service.list(activeOnly);
    }

    @GetMapping("/status")
    @PreAuthorize("isAuthenticated()")
    public OpenStatus status() {
        return service.status();
    }

    @PostMapping("/windows")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_BENEFITS)
    public WindowResponse create(@Valid @RequestBody WindowRequest req) {
        return service.create(req);
    }

    @PutMapping("/windows/{id}")
    @PreAuthorize(SecurityRoles.WRITE_BENEFITS)
    public WindowResponse update(@PathVariable UUID id, @Valid @RequestBody WindowRequest req) {
        return service.update(id, req);
    }
}
