package az.millers.hcm.compbenefits.api;

import az.millers.hcm.security.SecurityRoles;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.common.PageResponse;
import az.millers.hcm.compbenefits.api.dto.BonusRunGenerateRequest;
import az.millers.hcm.compbenefits.api.dto.BonusRunItemResponse;
import az.millers.hcm.compbenefits.api.dto.BonusRunPushRequest;
import az.millers.hcm.compbenefits.api.dto.BonusRunResponse;
import az.millers.hcm.compbenefits.domain.BonusRun;
import az.millers.hcm.compbenefits.domain.BonusRunStatus;
import az.millers.hcm.compbenefits.service.BonusRunService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/compbenefits/bonus-runs")
public class BonusRunController {

    private final BonusRunService service;

    public BonusRunController(BonusRunService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public PageResponse<BonusRunResponse> list(
            @RequestParam(required = false) UUID cycleId,
            @RequestParam(required = false) BonusRunStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<BonusRun> result = service.list(cycleId, status, PageRequest.of(page, size));
        return PageResponse.of(result, BonusRunResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public BonusRunResponse get(@PathVariable UUID id) {
        return BonusRunResponse.from(service.get(id));
    }

    @GetMapping("/{id}/items")
    @PreAuthorize("isAuthenticated()")
    public List<BonusRunItemResponse> items(@PathVariable UUID id) {
        return service.itemsFor(id).stream().map(BonusRunItemResponse::from).toList();
    }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public BonusRunResponse generate(@Valid @RequestBody BonusRunGenerateRequest req) {
        return BonusRunResponse.from(service.generate(req));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public BonusRunResponse submit(@PathVariable UUID id) {
        return BonusRunResponse.from(service.submit(id));
    }

    @PostMapping("/{id}/push")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public BonusRunResponse push(@PathVariable UUID id, @Valid @RequestBody BonusRunPushRequest req) {
        return BonusRunResponse.from(service.push(id, req.targetPayrollRunId()));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public BonusRunResponse cancel(@PathVariable UUID id,
                                     @RequestParam(required = false) String reason) {
        return BonusRunResponse.from(service.cancel(id, reason));
    }
}
