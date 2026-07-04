package az.millers.hcm.hr.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.selfservice.api.dto.HrServiceRequestDto;
import az.millers.hcm.selfservice.domain.ServiceRequestCategory;
import az.millers.hcm.selfservice.domain.ServiceRequestStatus;
import az.millers.hcm.selfservice.service.HrServiceRequestService;

/**
 * M429 — HR queue: service request management (assign, resolve, close).
 */
@RestController
@RequestMapping("/api/hr/service-requests")
public class HrServiceQueueController {

    private final HrServiceRequestService service;

    public HrServiceQueueController(HrServiceRequestService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'HR_SPECIALIST')")
    public List<HrServiceRequestDto> queue(
            @RequestParam(required = false) ServiceRequestCategory category,
            @RequestParam(required = false) ServiceRequestStatus status,
            @RequestParam(required = false, defaultValue = "false") boolean overdue) {
        return service.queue(category, status, overdue).stream()
                .map(HrServiceRequestDto::from)
                .toList();
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'HR_SPECIALIST')")
    public HrServiceRequestDto assign(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        return HrServiceRequestDto.from(service.assign(id, body.get("assignToUsername")));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'HR_SPECIALIST')")
    public HrServiceRequestDto start(@PathVariable UUID id) {
        return HrServiceRequestDto.from(service.start(id));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'HR_SPECIALIST')")
    public HrServiceRequestDto resolve(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        return HrServiceRequestDto.from(service.resolve(id, body.get("resolutionNotes")));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'HR_SPECIALIST')")
    public HrServiceRequestDto close(@PathVariable UUID id) {
        return HrServiceRequestDto.from(service.close(id));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasAnyRole('HR_ADMIN', 'HR_SPECIALIST')")
    public HrServiceRequestDto reopen(@PathVariable UUID id) {
        return HrServiceRequestDto.from(service.reopen(id));
    }
}
