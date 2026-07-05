package az.millers.hcm.employeerelations.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import az.millers.hcm.employeerelations.domain.*;
import az.millers.hcm.employeerelations.service.ErCaseService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M445 — ER case controller.
 * Access: HR_ADMIN, HR_SPECIALIST, SYSTEM_ADMIN.
 * Confidential gating in service layer.
 */
@RestController
@RequestMapping("/api/er/cases")
public class ErCaseController {

    private final ErCaseService service;

    public ErCaseController(ErCaseService service) {
        this.service = service;
    }

    public record CreateCaseRequest(
            ErCaseType caseType,
            String category,
            ErCaseSeverity severity,
            UUID employeeId,
            String description,
            Boolean isConfidential,
            Boolean isAnonymous) {}

    public record UpdateStatusRequest(
            ErCaseStatus status,
            String outcome) {}

    public record UpdateDetailsRequest(
            String category,
            ErCaseSeverity severity,
            String description,
            String ownerUsername,
            Boolean legalHold) {}

    public record AddNoteRequest(
            String body,
            Boolean isInternal) {}

    public record OpenInvestigationRequest(
            String investigatorUsername) {}

    public record CloseInvestigationRequest(
            String findings,
            String recommendation) {}

    public record AddInterviewRequest(
            String intervieweeName,
            String intervieweeRole,
            LocalDate interviewDate,
            String summary) {}

    public record AddEvidenceRequest(
            String description,
            UUID attachmentId) {}

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public ErCase create(@RequestBody CreateCaseRequest req) {
        return service.create(
                req.caseType(),
                req.category(),
                req.severity(),
                req.employeeId(),
                req.description(),
                req.isConfidential(),
                req.isAnonymous());
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<ErCase> list(
            @RequestParam(required = false) ErCaseType type,
            @RequestParam(required = false) ErCaseSeverity severity,
            @RequestParam(required = false) ErCaseStatus status) {
        return service.list(type, severity, status);
    }

    @GetMapping("/{id}")
    @PreAuthorize(SecurityRoles.READ_HR)
    public ErCase get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public ErCase updateStatus(@PathVariable UUID id, @RequestBody UpdateStatusRequest req) {
        return service.updateStatus(id, req.status(), req.outcome());
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public ErCase updateDetails(@PathVariable UUID id, @RequestBody UpdateDetailsRequest req) {
        return service.updateDetails(id, req.category(), req.severity(),
                req.description(), req.ownerUsername(), req.legalHold());
    }

    @PostMapping("/{id}/notes")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public ErCaseNote addNote(@PathVariable UUID id, @RequestBody AddNoteRequest req) {
        return service.addNote(id, req.body(), req.isInternal());
    }

    @GetMapping("/{id}/notes")
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<ErCaseNote> getNotes(@PathVariable UUID id) {
        return service.getNotes(id);
    }

    @PostMapping("/{id}/investigations")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public ErInvestigation openInvestigation(@PathVariable UUID id,
                                             @RequestBody OpenInvestigationRequest req) {
        return service.openInvestigation(id, req.investigatorUsername());
    }

    @GetMapping("/{id}/investigations")
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<ErInvestigation> getInvestigations(@PathVariable UUID id) {
        return service.getInvestigations(id);
    }

    @PutMapping("/investigations/{investigationId}/close")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public ErInvestigation closeInvestigation(@PathVariable UUID investigationId,
                                              @RequestBody CloseInvestigationRequest req) {
        return service.closeInvestigation(investigationId, req.findings(), req.recommendation());
    }

    @PostMapping("/investigations/{investigationId}/interviews")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public ErInvestigationInterview addInterview(@PathVariable UUID investigationId,
                                                 @RequestBody AddInterviewRequest req) {
        return service.addInterview(investigationId, req.intervieweeName(),
                req.intervieweeRole(), req.interviewDate(), req.summary());
    }

    @GetMapping("/investigations/{investigationId}/interviews")
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<ErInvestigationInterview> getInterviews(@PathVariable UUID investigationId) {
        return service.getInterviews(investigationId);
    }

    @PostMapping("/investigations/{investigationId}/evidence")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public ErEvidence addEvidence(@PathVariable UUID investigationId,
                                  @RequestBody AddEvidenceRequest req) {
        return service.addEvidence(investigationId, req.description(), req.attachmentId());
    }

    @GetMapping("/investigations/{investigationId}/evidence")
    @PreAuthorize(SecurityRoles.READ_HR)
    public List<ErEvidence> getEvidence(@PathVariable UUID investigationId) {
        return service.getEvidence(investigationId);
    }
}
