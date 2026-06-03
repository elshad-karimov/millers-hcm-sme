package az.millers.hcm.compbenefits.api;

import az.millers.hcm.security.SecurityRoles;

import java.time.LocalDate;
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

import az.millers.hcm.compbenefits.api.dto.BonusMatrixRuleRequest;
import az.millers.hcm.compbenefits.api.dto.BonusMatrixRuleResponse;
import az.millers.hcm.compbenefits.service.BonusMatrixService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/compbenefits/matrix-rules")
public class BonusMatrixController {

    private final BonusMatrixService service;

    public BonusMatrixController(BonusMatrixService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<BonusMatrixRuleResponse> list(
            @RequestParam(required = false) LocalDate effectiveOn) {
        var rows = effectiveOn == null ? service.listAll() : service.activeOn(effectiveOn);
        return rows.stream().map(BonusMatrixRuleResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public BonusMatrixRuleResponse get(@PathVariable UUID id) {
        return BonusMatrixRuleResponse.from(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public BonusMatrixRuleResponse create(@Valid @RequestBody BonusMatrixRuleRequest req) {
        return BonusMatrixRuleResponse.from(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public BonusMatrixRuleResponse update(@PathVariable UUID id,
                                            @Valid @RequestBody BonusMatrixRuleRequest req) {
        return BonusMatrixRuleResponse.from(service.update(id, req));
    }
}
