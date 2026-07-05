package az.millers.hcm.corehr.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import az.millers.hcm.corehr.service.SignatureService;
import az.millers.hcm.corehr.service.SignatureService.SignatureRequestDTO;

/**
 * M440 — Employee self-service for signature requests.
 */
@RestController
@RequestMapping("/api/self/signatures")
public class SelfSignatureController {

    private final SignatureService signatureService;

    public SelfSignatureController(SignatureService signatureService) {
        this.signatureService = signatureService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<SignatureRequestDTO> listMy() {
        return signatureService.listMy();
    }

    @PostMapping("/{id}/sign")
    @PreAuthorize("isAuthenticated()")
    public SignatureRequestDTO sign(@PathVariable UUID id) {
        return signatureService.sign(id);
    }

    public record DeclineInput(String reason) {}

    @PostMapping("/{id}/decline")
    @PreAuthorize("isAuthenticated()")
    public SignatureRequestDTO decline(@PathVariable UUID id, @RequestBody DeclineInput input) {
        return signatureService.decline(id, input.reason());
    }
}
