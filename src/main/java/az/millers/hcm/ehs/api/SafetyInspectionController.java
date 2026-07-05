package az.millers.hcm.ehs.api;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import az.millers.hcm.ehs.api.dto.SafetyInspectionDtos.*;
import az.millers.hcm.ehs.domain.InspectionStatus;
import az.millers.hcm.ehs.domain.SafetyInspection;
import az.millers.hcm.ehs.service.SafetyInspectionService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M450 — Safety inspection management.
 */
@RestController
@RequestMapping("/api/ehs/inspections")
public class SafetyInspectionController {

    private final SafetyInspectionService service;

    public SafetyInspectionController(SafetyInspectionService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public SafetyInspectionResponse create(@RequestBody CreateSafetyInspectionRequest req) {
        List<SafetyInspectionService.FindingInput> findings = req.findings() != null
                ? req.findings().stream()
                .map(f -> new SafetyInspectionService.FindingInput(
                        f.itemLabel(), f.findingStatus(), f.notes(), f.correctiveActionId()))
                .toList()
                : null;

        SafetyInspection inspection = service.create(
                req.workLocationId(),
                req.inspectionDate(),
                req.inspectorUsername(),
                req.title(),
                req.notes(),
                findings
        );

        return SafetyInspectionResponse.from(inspection);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public SafetyInspectionResponse update(@PathVariable UUID id,
                                            @RequestBody UpdateSafetyInspectionRequest req) {
        List<SafetyInspectionService.FindingInput> findings = req.findings() != null
                ? req.findings().stream()
                .map(f -> new SafetyInspectionService.FindingInput(
                        f.itemLabel(), f.findingStatus(), f.notes(), f.correctiveActionId()))
                .toList()
                : null;

        SafetyInspection inspection = service.update(id, req.title(), req.notes(), findings);

        return SafetyInspectionResponse.from(inspection);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public SafetyInspectionResponse complete(@PathVariable UUID id) {
        service.complete(id);
        SafetyInspection inspection = service.get(id);
        return SafetyInspectionResponse.from(inspection);
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_HR)
    public SafetyInspectionResponse get(@PathVariable UUID id) {
        SafetyInspection inspection = service.get(id);
        return SafetyInspectionResponse.from(inspection);
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<SafetyInspectionResponse> list(
            @RequestParam(required = false) InspectionStatus status) {

        return service.list(status).stream()
                .map(SafetyInspectionResponse::from)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}/findings")
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<InspectionFindingResponse> getFindings(@PathVariable UUID id) {
        return service.getFindings(id).stream()
                .map(InspectionFindingResponse::from)
                .collect(Collectors.toList());
    }
}
