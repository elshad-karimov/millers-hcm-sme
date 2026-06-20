package az.millers.hcm.lifecycle.api;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.lifecycle.api.dto.OffboardingDtos.BenefitsClosureResponse;
import az.millers.hcm.lifecycle.api.dto.OffboardingDtos.BenefitsClosureUpdateRequest;
import az.millers.hcm.lifecycle.api.dto.OffboardingDtos.OffboardingCaseResponse;
import az.millers.hcm.lifecycle.api.dto.OffboardingDtos.RehireEligibilityRequest;
import az.millers.hcm.lifecycle.service.OffboardingBenefitsService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/lifecycle/offboarding")
@RequiredArgsConstructor
public class OffboardingBenefitsController {

    private final OffboardingBenefitsService service;

    @GetMapping("/cases/{caseId}/benefits")
    @Secured("ROLE_READ_HR")
    public List<BenefitsClosureResponse> listBenefits(@PathVariable UUID caseId) {
        return service.getBenefits(caseId).stream()
                .map(BenefitsClosureResponse::from).toList();
    }

    @PatchMapping("/benefits/{benefitId}")
    @Secured("ROLE_WRITE_HR")
    public BenefitsClosureResponse update(@PathVariable UUID benefitId,
                                           @RequestBody BenefitsClosureUpdateRequest req) {
        return BenefitsClosureResponse.from(service.update(benefitId, req));
    }

    @PatchMapping("/cases/{caseId}/rehire-eligibility")
    @Secured("ROLE_WRITE_HR")
    public OffboardingCaseResponse updateRehire(@PathVariable UUID caseId,
                                                 @RequestBody RehireEligibilityRequest req) {
        return OffboardingCaseResponse.from(service.updateRehireEligibility(caseId, req));
    }
}
