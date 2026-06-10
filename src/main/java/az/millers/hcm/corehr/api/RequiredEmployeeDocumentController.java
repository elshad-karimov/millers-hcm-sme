package az.millers.hcm.corehr.api;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.corehr.domain.DocumentRequirementType;
import az.millers.hcm.corehr.domain.RequiredDocumentStatus;
import az.millers.hcm.corehr.domain.RequiredEmployeeDocument;
import az.millers.hcm.corehr.service.RequiredEmployeeDocumentService;
import az.millers.hcm.security.SecurityRoles;

/** M262 — Required employee document REST (PRD §29). */
@RestController
@RequestMapping("/api/employees/{employeeId}/required-documents")
public class RequiredEmployeeDocumentController {

    private final RequiredEmployeeDocumentService service;

    public RequiredEmployeeDocumentController(RequiredEmployeeDocumentService service) {
        this.service = service;
    }

    public record DocResponse(
            UUID id,
            UUID employeeId,
            DocumentRequirementType documentType,
            String label,
            LocalDate requiredByDate,
            RequiredDocumentStatus status,
            UUID attachmentId,
            OffsetDateTime satisfiedAt,
            String satisfiedBy,
            String source,
            UUID sourceGrantId,
            String notes,
            OffsetDateTime createdAt,
            String createdBy,
            OffsetDateTime updatedAt,
            String updatedBy) {

        public static DocResponse from(RequiredEmployeeDocument d) {
            return new DocResponse(
                    d.getId(), d.getEmployeeId(),
                    d.getDocumentType(), d.getLabel(),
                    d.getRequiredByDate(), d.getStatus(),
                    d.getAttachmentId(), d.getSatisfiedAt(), d.getSatisfiedBy(),
                    d.getSource(), d.getSourceGrantId(), d.getNotes(),
                    d.getCreatedAt(), d.getCreatedBy(),
                    d.getUpdatedAt(), d.getUpdatedBy());
        }
    }

    public record AssignRequest(
            @NotNull DocumentRequirementType documentType,
            @NotBlank @Size(max = 200) String label,
            LocalDate requiredByDate,
            @Size(max = 2000) String notes) {}

    public record SatisfyRequest(@NotNull UUID attachmentId) {}

    public record WaiveRequest(@Size(max = 2000) String reason) {}

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<DocResponse> list(@PathVariable UUID employeeId) {
        return service.listForEmployee(employeeId).stream()
                .map(DocResponse::from).toList();
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public DocResponse assign(@PathVariable UUID employeeId,
                               @Valid @RequestBody AssignRequest req) {
        return DocResponse.from(service.assign(
                employeeId, req.documentType(), req.label(),
                req.requiredByDate(), "MANUAL", null, req.notes()));
    }

    @PostMapping("/{id}/satisfy")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public DocResponse satisfy(@PathVariable UUID employeeId,
                                @PathVariable UUID id,
                                @Valid @RequestBody SatisfyRequest req) {
        return service.satisfy(id, req.attachmentId())
                .map(DocResponse::from)
                .orElseThrow(() -> new az.millers.hcm.common.ResourceNotFoundException(
                        "Required document not found: " + id));
    }

    @PostMapping("/{id}/waive")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public DocResponse waive(@PathVariable UUID employeeId,
                              @PathVariable UUID id,
                              @RequestBody(required = false) WaiveRequest req) {
        return service.waive(id, req == null ? null : req.reason())
                .map(DocResponse::from)
                .orElseThrow(() -> new az.millers.hcm.common.ResourceNotFoundException(
                        "Required document not found: " + id));
    }
}
