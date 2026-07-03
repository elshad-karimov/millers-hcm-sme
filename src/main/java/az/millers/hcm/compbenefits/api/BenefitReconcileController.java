package az.millers.hcm.compbenefits.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.compbenefits.api.dto.BenefitReconcileDtos.ReconcileRequest;
import az.millers.hcm.compbenefits.api.dto.BenefitReconcileDtos.ReconcileResponse;
import az.millers.hcm.compbenefits.service.BenefitReconciliationService;
import az.millers.hcm.security.SecurityRoles;
import jakarta.validation.Valid;

/** Provider-file reconciliation API (HCM_11 M384). */
@RestController
@RequestMapping("/api/compbenefits/reconcile")
public class BenefitReconcileController {

    private final BenefitReconciliationService service;

    public BenefitReconcileController(BenefitReconciliationService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_BENEFITS)
    public ReconcileResponse reconcile(@Valid @RequestBody ReconcileRequest req) {
        return service.reconcile(req.providerId(), req.rows());
    }
}
