package az.millers.hcm.recruitment.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.recruitment.api.dto.CandidateDocumentDtos.AddItemRequest;
import az.millers.hcm.recruitment.api.dto.CandidateDocumentDtos.Checklist;
import az.millers.hcm.recruitment.api.dto.CandidateDocumentDtos.ItemResponse;
import az.millers.hcm.recruitment.api.dto.CandidateDocumentDtos.StatusUpdate;
import az.millers.hcm.recruitment.api.dto.CandidateDocumentDtos.TypeRequest;
import az.millers.hcm.recruitment.api.dto.CandidateDocumentDtos.TypeResponse;
import az.millers.hcm.recruitment.service.DocumentChecklistService;
import az.millers.hcm.security.SecurityRoles;

/** M292 — Recruitment PRD §12 document-checklist REST. */
@RestController
@RequestMapping("/api/recruitment")
public class DocumentChecklistController {

    private final DocumentChecklistService service;

    public DocumentChecklistController(DocumentChecklistService service) {
        this.service = service;
    }

    // ── Document-type catalog ──────────────────────────────────────────

    @GetMapping("/document-types")
    @PreAuthorize(SecurityRoles.READ_RECRUITMENT)
    public List<TypeResponse> listTypes(@RequestParam(defaultValue = "true") boolean activeOnly) {
        return service.listTypes(activeOnly);
    }

    @PostMapping("/document-types")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public TypeResponse createType(@Valid @RequestBody TypeRequest req) {
        return service.createType(req);
    }

    @PutMapping("/document-types/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public TypeResponse updateType(@PathVariable UUID id, @Valid @RequestBody TypeRequest req) {
        return service.updateType(id, req);
    }

    // ── Per-candidate checklist ────────────────────────────────────────

    @GetMapping("/candidates/{candidateId}/documents")
    @PreAuthorize(SecurityRoles.READ_RECRUITMENT)
    public Checklist checklist(@PathVariable UUID candidateId) {
        return service.checklist(candidateId);
    }

    @PostMapping("/candidates/{candidateId}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_RECRUITMENT)
    public ItemResponse addItem(@PathVariable UUID candidateId,
                                 @Valid @RequestBody AddItemRequest req) {
        return service.addItem(candidateId, req);
    }

    @PostMapping("/candidates/{candidateId}/documents/seed-defaults")
    @PreAuthorize(SecurityRoles.WRITE_RECRUITMENT)
    public Checklist seedDefaults(@PathVariable UUID candidateId) {
        return service.seedDefaults(candidateId);
    }

    @PutMapping("/candidate-documents/{id}")
    @PreAuthorize(SecurityRoles.WRITE_RECRUITMENT)
    public ItemResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody StatusUpdate req) {
        return service.updateStatus(id, req);
    }

    @DeleteMapping("/candidate-documents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(SecurityRoles.WRITE_RECRUITMENT)
    public void deleteItem(@PathVariable UUID id) {
        service.deleteItem(id);
    }
}
