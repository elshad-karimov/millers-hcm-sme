package az.millers.hcm.staffing.api;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.security.SecurityRoles;
import az.millers.hcm.staffing.api.dto.PositionSplitMergeDtos.MergeRequest;
import az.millers.hcm.staffing.api.dto.PositionSplitMergeDtos.MergeResult;
import az.millers.hcm.staffing.api.dto.PositionSplitMergeDtos.SplitRequest;
import az.millers.hcm.staffing.api.dto.PositionSplitMergeDtos.SplitResult;
import az.millers.hcm.staffing.service.PositionSplitMergeService;

/** M273 — PRD §41 Split & Merge REST. */
@RestController
@RequestMapping("/api/positions")
public class PositionSplitMergeController {

    private final PositionSplitMergeService service;

    public PositionSplitMergeController(PositionSplitMergeService service) {
        this.service = service;
    }

    @PostMapping("/{id}/split")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public SplitResult split(@PathVariable UUID id,
                              @Valid @RequestBody SplitRequest req) {
        return service.split(id, req);
    }

    @PostMapping("/merge")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public MergeResult merge(@Valid @RequestBody MergeRequest req) {
        return service.merge(req);
    }
}
