package az.millers.hcm.compensation.api;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.compensation.api.dto.ChangeReasonRequest;
import az.millers.hcm.compensation.api.dto.ChangeReasonResponse;
import az.millers.hcm.compensation.service.ChangeReasonService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M360 — Change reason CRUD endpoints.
 */
@RestController
@RequestMapping("/api/compensation/change-reasons")
public class ChangeReasonController {

    private final ChangeReasonService service;

    public ChangeReasonController(ChangeReasonService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_COMPENSATION)
    public List<ChangeReasonResponse> list() {
        return service.listActive().stream()
                .map(ChangeReasonResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_COMPENSATION)
    public ChangeReasonResponse get(@PathVariable UUID id) {
        return ChangeReasonResponse.from(service.get(id));
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_COMPENSATION)
    @ResponseStatus(HttpStatus.CREATED)
    public ChangeReasonResponse create(@RequestBody ChangeReasonRequest req) {
        return ChangeReasonResponse.from(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_COMPENSATION)
    public ChangeReasonResponse update(@PathVariable UUID id, @RequestBody ChangeReasonRequest req) {
        return ChangeReasonResponse.from(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_COMPENSATION)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
