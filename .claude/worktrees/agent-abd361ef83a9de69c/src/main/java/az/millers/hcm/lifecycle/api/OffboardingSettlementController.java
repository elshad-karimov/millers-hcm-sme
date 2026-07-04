package az.millers.hcm.lifecycle.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.lifecycle.api.dto.OffboardingDtos.SettlementComponentRequest;
import az.millers.hcm.lifecycle.api.dto.OffboardingDtos.SettlementComponentResponse;
import az.millers.hcm.lifecycle.api.dto.OffboardingDtos.SettlementResponse;
import az.millers.hcm.lifecycle.api.dto.OffboardingDtos.SettlementUpdateRequest;
import az.millers.hcm.lifecycle.domain.OffboardingSettlement;
import az.millers.hcm.lifecycle.domain.SettlementStatus;
import az.millers.hcm.lifecycle.service.OffboardingSettlementService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/lifecycle/offboarding")
@RequiredArgsConstructor
public class OffboardingSettlementController {

    private final OffboardingSettlementService service;

    @GetMapping("/cases/{caseId}/settlement")
    @Secured("ROLE_READ_HR")
    public ResponseEntity<SettlementResponse> get(@PathVariable UUID caseId) {
        return service.findByCaseId(caseId)
                .map(s -> SettlementResponse.from(s, service.getComponents(s.getId())))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/cases/{caseId}/settlement")
    @Secured("ROLE_WRITE_HR")
    public SettlementResponse getOrCreate(@PathVariable UUID caseId, Authentication auth) {
        OffboardingSettlement s = service.getOrCreate(caseId, auth.getName());
        return SettlementResponse.from(s, service.getComponents(s.getId()));
    }

    @PatchMapping("/settlements/{settlementId}")
    @Secured("ROLE_WRITE_HR")
    public SettlementResponse update(@PathVariable UUID settlementId,
                                      @RequestBody SettlementUpdateRequest req) {
        OffboardingSettlement s = service.update(settlementId, req);
        return SettlementResponse.from(s, service.getComponents(settlementId));
    }

    @PostMapping("/settlements/{settlementId}/components")
    @Secured("ROLE_WRITE_HR")
    public SettlementComponentResponse addComponent(@PathVariable UUID settlementId,
                                                     @RequestBody SettlementComponentRequest req) {
        return SettlementComponentResponse.from(service.addComponent(settlementId, req));
    }

    @DeleteMapping("/settlements/components/{componentId}")
    @Secured("ROLE_WRITE_HR")
    public ResponseEntity<Void> deleteComponent(@PathVariable UUID componentId) {
        service.deleteComponent(componentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/settlements/{settlementId}/status")
    @Secured("ROLE_WRITE_HR")
    public SettlementResponse transition(@PathVariable UUID settlementId,
                                          @RequestParam SettlementStatus status,
                                          Authentication auth) {
        OffboardingSettlement s = service.transitionStatus(settlementId, status, auth.getName());
        return SettlementResponse.from(s, service.getComponents(settlementId));
    }

    @GetMapping("/settlements/{settlementId}/components")
    @Secured("ROLE_READ_HR")
    public List<SettlementComponentResponse> listComponents(@PathVariable UUID settlementId) {
        return service.getComponents(settlementId).stream()
                .map(SettlementComponentResponse::from).toList();
    }
}
