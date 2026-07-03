package az.millers.hcm.performance.api;

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

import az.millers.hcm.performance.api.dto.OkrDtos.CheckInRequest;
import az.millers.hcm.performance.api.dto.OkrDtos.CheckInResponse;
import az.millers.hcm.performance.api.dto.OkrDtos.KeyResultRequest;
import az.millers.hcm.performance.api.dto.OkrDtos.KeyResultResponse;
import az.millers.hcm.performance.api.dto.OkrDtos.ObjectiveRequest;
import az.millers.hcm.performance.api.dto.OkrDtos.ObjectiveResponse;
import az.millers.hcm.performance.service.OkrService;
import az.millers.hcm.security.SecurityRoles;
import jakarta.validation.Valid;

/** OKR objective / key-result / check-in API (HCM_12 M391). */
@RestController
@RequestMapping("/api/performance/okrs")
public class OkrController {

    private final OkrService service;

    public OkrController(OkrService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<ObjectiveResponse> list(@RequestParam(required = false) UUID cycleId,
                                        @RequestParam(required = false) String level,
                                        @RequestParam(required = false) UUID ownerEmployeeId) {
        return service.list(cycleId, level, ownerEmployeeId);
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public ObjectiveResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_PLUS_MANAGERS)
    public ObjectiveResponse create(@Valid @RequestBody ObjectiveRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_PLUS_MANAGERS)
    public ObjectiveResponse update(@PathVariable UUID id, @Valid @RequestBody ObjectiveRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/status/{status}")
    @PreAuthorize(SecurityRoles.WRITE_HR_PLUS_MANAGERS)
    public ObjectiveResponse changeStatus(@PathVariable UUID id, @PathVariable String status) {
        return service.changeStatus(id, status);
    }

    @PostMapping("/{id}/key-results")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_PLUS_MANAGERS)
    public KeyResultResponse addKeyResult(@PathVariable UUID id, @Valid @RequestBody KeyResultRequest req) {
        return service.addKeyResult(id, req);
    }

    @PutMapping("/key-results/{krId}")
    @PreAuthorize(SecurityRoles.WRITE_HR_PLUS_MANAGERS)
    public KeyResultResponse updateKeyResult(@PathVariable UUID krId, @Valid @RequestBody KeyResultRequest req) {
        return service.updateKeyResult(krId, req);
    }

    @PostMapping("/{id}/check-ins")
    @PreAuthorize(SecurityRoles.WRITE_HR_PLUS_MANAGERS)
    public ObjectiveResponse checkIn(@PathVariable UUID id, @Valid @RequestBody CheckInRequest req) {
        return service.checkIn(id, req);
    }

    @GetMapping("/{id}/check-ins")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<CheckInResponse> checkInHistory(@PathVariable UUID id) {
        return service.checkInHistory(id);
    }
}
