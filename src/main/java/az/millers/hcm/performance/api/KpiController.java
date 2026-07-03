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

import az.millers.hcm.performance.api.dto.KpiDtos.AssignRequest;
import az.millers.hcm.performance.api.dto.KpiDtos.AssignmentResponse;
import az.millers.hcm.performance.api.dto.KpiDtos.KpiRequest;
import az.millers.hcm.performance.api.dto.KpiDtos.KpiResponse;
import az.millers.hcm.performance.api.dto.KpiDtos.MeasureRequest;
import az.millers.hcm.performance.api.dto.KpiDtos.ResultResponse;
import az.millers.hcm.performance.service.KpiService;
import az.millers.hcm.security.SecurityRoles;
import jakarta.validation.Valid;

/** KPI library + assignment API (HCM_12 M390). */
@RestController
@RequestMapping("/api/performance/kpis")
public class KpiController {

    private final KpiService service;

    public KpiController(KpiService service) {
        this.service = service;
    }

    // ---------- Library ----------

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<KpiResponse> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return service.listKpis(activeOnly);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public KpiResponse create(@Valid @RequestBody KpiRequest req) {
        return service.createKpi(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public KpiResponse update(@PathVariable UUID id, @Valid @RequestBody KpiRequest req) {
        return service.updateKpi(id, req);
    }

    // ---------- Assignments ----------

    @GetMapping("/assignments")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<AssignmentResponse> assignments(@RequestParam(required = false) UUID cycleId,
                                                @RequestParam(required = false) UUID employeeId) {
        return service.listAssignments(cycleId, employeeId);
    }

    @PostMapping("/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_PLUS_MANAGERS)
    public AssignmentResponse assign(@Valid @RequestBody AssignRequest req) {
        return service.assign(req);
    }

    @PostMapping("/assignments/{id}/measure")
    @PreAuthorize(SecurityRoles.WRITE_HR_PLUS_MANAGERS)
    public AssignmentResponse measure(@PathVariable UUID id, @Valid @RequestBody MeasureRequest req) {
        return service.measure(id, req);
    }

    @PostMapping("/assignments/{id}/cancel")
    @PreAuthorize(SecurityRoles.WRITE_HR_PLUS_MANAGERS)
    public AssignmentResponse cancel(@PathVariable UUID id) {
        return service.cancelAssignment(id);
    }

    @GetMapping("/assignments/{id}/history")
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public List<ResultResponse> history(@PathVariable UUID id) {
        return service.history(id);
    }
}
