package az.millers.hcm.compensation.api;

import java.math.BigDecimal;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.compensation.api.dto.CompConfigDto;
import az.millers.hcm.compensation.domain.BudgetExceededPolicy;
import az.millers.hcm.compensation.service.CompConfigService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M359 — Tenant compensation configuration endpoints.
 */
@RestController
@RequestMapping("/api/compensation/config")
public class CompConfigController {

    private final CompConfigService service;

    public CompConfigController(CompConfigService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_COMPENSATION)
    public CompConfigDto get() {
        return CompConfigDto.from(service.get());
    }

    @PutMapping
    @PreAuthorize(SecurityRoles.WRITE_COMPENSATION)
    public CompConfigDto update(@RequestBody CompConfigUpdateRequest req) {
        return CompConfigDto.from(service.update(
            req.maxIncreasePctWithoutApproval,
            req.budgetExceededPolicy,
            req.defaultCurrency
        ));
    }

    public record CompConfigUpdateRequest(
        BigDecimal maxIncreasePctWithoutApproval,
        BudgetExceededPolicy budgetExceededPolicy,
        String defaultCurrency
    ) {}
}
