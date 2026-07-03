package az.millers.hcm.compbenefits.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.compbenefits.api.dto.BenefitPlanConfigDtos.EligibilityReplaceRequest;
import az.millers.hcm.compbenefits.api.dto.BenefitPlanConfigDtos.EligibilityRuleResponse;
import az.millers.hcm.compbenefits.api.dto.BenefitPlanConfigDtos.TierResponse;
import az.millers.hcm.compbenefits.api.dto.BenefitPlanConfigDtos.TiersReplaceRequest;
import az.millers.hcm.compbenefits.service.BenefitPlanConfigService;
import az.millers.hcm.security.SecurityRoles;
import jakarta.validation.Valid;

/**
 * Plan coverage-tier + eligibility-rule sub-resources (HCM_11 M375).
 * Reads for benefits readers (drive enrolment dropdowns); full-replace writes for admin.
 */
@RestController
@RequestMapping("/api/compbenefits/benefits/plans/{planId}")
public class BenefitPlanConfigController {

    private final BenefitPlanConfigService service;

    public BenefitPlanConfigController(BenefitPlanConfigService service) {
        this.service = service;
    }

    // ---------- Coverage tiers ----------

    @GetMapping("/tiers")
    @PreAuthorize(SecurityRoles.READ_BENEFITS_PLUS_MANAGERS)
    public List<TierResponse> listTiers(@PathVariable UUID planId) {
        return service.listTiers(planId);
    }

    @PutMapping("/tiers")
    @PreAuthorize(SecurityRoles.WRITE_BENEFITS)
    public List<TierResponse> replaceTiers(@PathVariable UUID planId,
                                           @Valid @RequestBody TiersReplaceRequest req) {
        return service.replaceTiers(planId, req.tiers());
    }

    // ---------- Eligibility rules ----------

    @GetMapping("/eligibility-rules")
    @PreAuthorize(SecurityRoles.READ_BENEFITS_PLUS_MANAGERS)
    public List<EligibilityRuleResponse> listRules(@PathVariable UUID planId) {
        return service.listRules(planId);
    }

    @PutMapping("/eligibility-rules")
    @PreAuthorize(SecurityRoles.WRITE_BENEFITS)
    public List<EligibilityRuleResponse> replaceRules(@PathVariable UUID planId,
                                                      @Valid @RequestBody EligibilityReplaceRequest req) {
        return service.replaceRules(planId, req.rules());
    }
}
