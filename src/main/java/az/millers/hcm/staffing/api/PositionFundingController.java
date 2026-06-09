package az.millers.hcm.staffing.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.staffing.api.dto.PositionBudgetDtos.FundingRequest;
import az.millers.hcm.staffing.api.dto.PositionBudgetDtos.FundingResponse;
import az.millers.hcm.staffing.domain.FundingStatus;
import az.millers.hcm.staffing.service.PositionFundingService;
import jakarta.validation.Valid;

/**
 * M244 — singleton funding state per position + the "list by status"
 * helper used by the Position Control "Funding" dashboard widget.
 */
@RestController
@RequestMapping("/api/positions")
public class PositionFundingController {

    private final PositionFundingService service;

    public PositionFundingController(PositionFundingService service) {
        this.service = service;
    }

    @GetMapping("/{positionId}/funding")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','RECRUITER','FINANCE_USER')")
    public FundingResponse get(@PathVariable UUID positionId) {
        return FundingResponse.from(service.get(positionId));
    }

    @PutMapping("/{positionId}/funding")
    @PreAuthorize("hasAnyRole('HR_ADMIN','FINANCE_USER')")
    public FundingResponse upsert(@PathVariable UUID positionId,
                                   @Valid @RequestBody FundingRequest req) {
        return FundingResponse.from(service.upsert(positionId, req.toEntity()));
    }

    @GetMapping("/funding/by-status")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','FINANCE_USER')")
    public List<FundingResponse> listByStatus(@RequestParam FundingStatus status) {
        return service.listByStatus(status).stream()
                .map(FundingResponse::from)
                .toList();
    }

    /**
     * M244 — Bulk map for the SPA's PositionsPage. Returns one entry per
     * funding row so the list view can render the funding pill without
     * an N+1 fetch per row.
     */
    @GetMapping("/funding/map")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','RECRUITER','FINANCE_USER')")
    public Map<UUID, FundingStatus> allByPositionId() {
        return service.listAll().stream()
                .collect(Collectors.toMap(
                        f -> f.getPositionId(), f -> f.getStatus()));
    }
}
