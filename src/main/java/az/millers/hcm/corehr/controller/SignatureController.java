package az.millers.hcm.corehr.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import az.millers.hcm.corehr.service.SignatureService;
import az.millers.hcm.corehr.service.SignatureService.CreateSignatureRequestInput;
import az.millers.hcm.corehr.service.SignatureService.SignatureRequestDTO;
import az.millers.hcm.security.SecurityRoles;

/**
 * M440 — Signature requests for documents (HR admin surface).
 */
@RestController
@RequestMapping("/api/documents/signatures")
public class SignatureController {

    private final SignatureService signatureService;

    public SignatureController(SignatureService signatureService) {
        this.signatureService = signatureService;
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public SignatureRequestDTO create(@RequestBody CreateSignatureRequestInput input) {
        return signatureService.create(input);
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<SignatureRequestDTO> listAll() {
        return signatureService.listAll();
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public void cancel(@PathVariable UUID id) {
        signatureService.cancel(id);
    }
}
