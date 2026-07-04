package az.millers.hcm.lifecycle.api;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.lifecycle.api.dto.ExitInterviewRequest;
import az.millers.hcm.lifecycle.api.dto.ExitInterviewResponse;
import az.millers.hcm.lifecycle.service.OffboardingExitInterviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/lifecycle/offboarding/cases/{caseId}/exit-interview")
@RequiredArgsConstructor
public class OffboardingExitInterviewController {

    private final OffboardingExitInterviewService service;

    @GetMapping
    @Secured("ROLE_READ_HR")
    public ResponseEntity<ExitInterviewResponse> get(@PathVariable UUID caseId) {
        return service.findByCaseId(caseId)
                .map(ExitInterviewResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Secured("ROLE_WRITE_HR")
    public ExitInterviewResponse save(@PathVariable UUID caseId,
                                       @Valid @RequestBody ExitInterviewRequest req) {
        return ExitInterviewResponse.from(service.save(caseId, req));
    }
}
