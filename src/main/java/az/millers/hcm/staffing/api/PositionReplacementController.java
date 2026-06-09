package az.millers.hcm.staffing.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.security.SecurityRoles;
import az.millers.hcm.staffing.api.dto.PositionOccupancyDtos.ReasonRequest;
import az.millers.hcm.staffing.api.dto.PositionOccupancyDtos.ReplacementRequest;
import az.millers.hcm.staffing.api.dto.PositionOccupancyDtos.ReplacementResponse;
import az.millers.hcm.staffing.domain.ReplacementStatus;
import az.millers.hcm.staffing.service.PositionReplacementService;
import jakarta.validation.Valid;

/** M246 — REST surface for the replacement workflow. */
@RestController
@RequestMapping("/api/position-replacements")
public class PositionReplacementController {

    private final PositionReplacementService service;

    public PositionReplacementController(PositionReplacementService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','MANAGER','DEPARTMENT_MANAGER')")
    public List<ReplacementResponse> list(@RequestParam(required = false) ReplacementStatus status) {
        var rows = status == null ? service.listByStatus(ReplacementStatus.PENDING_APPROVAL)
                : service.listByStatus(status);
        return rows.stream().map(ReplacementResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','MANAGER','DEPARTMENT_MANAGER')")
    public ReplacementResponse get(@PathVariable UUID id) {
        return ReplacementResponse.from(service.get(id));
    }

    @GetMapping("/by-position/{positionId}")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','MANAGER','DEPARTMENT_MANAGER')")
    public List<ReplacementResponse> byPosition(@PathVariable UUID positionId) {
        return service.forPosition(positionId).stream().map(ReplacementResponse::from).toList();
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public ReplacementResponse create(@Valid @RequestBody ReplacementRequest req) {
        return ReplacementResponse.from(service.create(req.toEntity()));
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public ReplacementResponse update(@PathVariable UUID id, @Valid @RequestBody ReplacementRequest req) {
        return ReplacementResponse.from(service.update(id, req.toEntity()));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public ReplacementResponse submit(@PathVariable UUID id) {
        return ReplacementResponse.from(service.submit(id));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public ReplacementResponse approve(@PathVariable UUID id) {
        return ReplacementResponse.from(service.approve(id));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public ReplacementResponse reject(@PathVariable UUID id, @Valid @RequestBody ReasonRequest req) {
        return ReplacementResponse.from(service.reject(id, req.reason()));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public ReplacementResponse complete(@PathVariable UUID id) {
        return ReplacementResponse.from(service.complete(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public ReplacementResponse cancel(@PathVariable UUID id, @Valid @RequestBody(required = false) ReasonRequest req) {
        return ReplacementResponse.from(service.cancel(id, req == null ? null : req.reason()));
    }
}
