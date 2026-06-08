package az.millers.hcm.letters.api;

import az.millers.hcm.security.SecurityRoles;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.security.CurrentRequest;

import java.util.List;

import az.millers.hcm.common.PageResponse;
import az.millers.hcm.letters.api.dto.LetterRequestResponse;
import az.millers.hcm.letters.api.dto.LetterSubmitRequest;
import az.millers.hcm.letters.domain.LetterStatus;
import az.millers.hcm.letters.repo.LetterTemplateRepository;
import az.millers.hcm.letters.service.LetterRequestService;
import az.millers.hcm.selfservice.service.EmployeeContextService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/letter-requests")
public class LetterRequestController {

    private final LetterRequestService service;
    private final LetterTemplateRepository templateRepo;
    private final EmployeeContextService context;
    private final CurrentRequest currentRequest;

    public LetterRequestController(LetterRequestService service,
                                    LetterTemplateRepository templateRepo,
                                    EmployeeContextService context,
                                    CurrentRequest currentRequest) {
        this.service = service;
        this.templateRepo = templateRepo;
        this.context = context;
        this.currentRequest = currentRequest;
    }

    /**
     * HR / scoped-manager queue. Scope filter is applied inside the service
     * so DEPARTMENT_MANAGER callers only see their team's requests.
     */
    @GetMapping
    @PreAuthorize(SecurityRoles.READ_HR_PLUS_MANAGERS)
    public PageResponse<LetterRequestResponse> list(
            @RequestParam(required = false) LetterStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size, Sort.by("requestedAt").descending());
        return PageResponse.of(service.list(status, pageable), LetterRequestResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public LetterRequestResponse get(@PathVariable UUID id) {
        return LetterRequestResponse.from(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public LetterRequestResponse create(@Valid @RequestBody LetterSubmitRequest req) {
        return LetterRequestResponse.from(service.submit(req));
    }

    /**
     * Self-service: employee requests a letter for themselves.
     * {@code employeeId} in the body is ignored and replaced with the
     * caller's own employee ID (M77 — PRD §8.10 self-service shortcut).
     */
    @PostMapping("/my-request")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public LetterRequestResponse myRequest(
            @Valid @RequestBody LetterSubmitRequest req) {
        UUID selfId = context.currentEmployee().getId();
        LetterSubmitRequest selfReq = new LetterSubmitRequest(
                selfId, req.templateId(), req.purpose(), req.customFields());
        return LetterRequestResponse.from(service.submit(selfReq));
    }

    /** Self-service: employee's own letter history. */
    @GetMapping("/mine")
    @PreAuthorize("isAuthenticated()")
    public List<LetterRequestResponse> mine() {
        UUID selfId = context.currentEmployee().getId();
        return service.mine(selfId).stream().map(LetterRequestResponse::from).toList();
    }

    /**
     * Download the rendered body. Returns 409 if the request is not ISSUED.
     * Content-Type follows the template's output_format.
     */
    @GetMapping("/{id}/body")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> body(@PathVariable UUID id) {
        var r = service.get(id);
        if (r.getStatus() != LetterStatus.ISSUED) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Letter has not been issued yet (status=" + r.getStatus() + ")");
        }
        var fmt = templateRepo.findById(r.getTemplateId())
                .map(t -> t.getOutputFormat().name())
                .orElse("TEXT");
        MediaType mime = "HTML".equals(fmt)
                ? MediaType.TEXT_HTML : MediaType.TEXT_PLAIN;
        String filename = (r.getRequestNo() == null ? "letter" : r.getRequestNo())
                + ("HTML".equals(fmt) ? ".html" : ".txt");
        return ResponseEntity.ok()
                .contentType(mime)
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(r.getRenderedBody() == null ? "" : r.getRenderedBody());
    }

    /**
     * M213 — HR / admin can cancel a PENDING or APPROVED letter request.
     * Reuses the same transition used by the workflow cancel callback.
     */
    @PostMapping("/{id}/cancel")
    @PreAuthorize(SecurityRoles.WRITE_HR)
    public LetterRequestResponse cancel(@PathVariable UUID id,
                                         @RequestParam(required = false) String reason) {
        return LetterRequestResponse.from(
                service.onCancelled(id, currentRequest.username(), reason));
    }

    /**
     * M139 — PDF download. Renders on demand with locale-resolved
     * template, signature line, and QR verification code.
     */
    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        var r = service.get(id);
        byte[] pdf = service.renderPdf(id);
        String filename = (r.getRequestNo() == null ? "letter" : r.getRequestNo()) + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "inline; filename=\"" + filename + "\"")
                .body(pdf);
    }
}
