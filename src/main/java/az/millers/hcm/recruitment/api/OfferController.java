package az.millers.hcm.recruitment.api;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.recruitment.api.dto.OfferRequest;
import az.millers.hcm.recruitment.api.dto.OfferResponse;
import az.millers.hcm.recruitment.domain.OfferStatus;
import az.millers.hcm.recruitment.service.OfferService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/recruitment/offers")
public class OfferController {

    private final OfferService service;

    public OfferController(OfferService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OfferResponse> getForApplication(@RequestParam UUID applicationId) {
        return service.findForApplication(applicationId)
                .map(o -> ResponseEntity.ok(OfferResponse.from(o)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST','RECRUITER')")
    public OfferResponse upsert(@RequestParam UUID applicationId,
                                 @Valid @RequestBody OfferRequest req) {
        return OfferResponse.from(service.createOrUpdate(applicationId, req));
    }

    @PostMapping("/{id}/status/{status}")
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST','RECRUITER')")
    public OfferResponse transition(@PathVariable UUID id,
                                     @PathVariable OfferStatus status,
                                     @RequestParam(required = false) String notes) {
        return OfferResponse.from(service.transition(id, status, notes));
    }
}
