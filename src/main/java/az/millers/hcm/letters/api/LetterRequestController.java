package az.millers.hcm.letters.api;

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

import az.millers.hcm.common.PageResponse;
import az.millers.hcm.letters.api.dto.LetterRequestResponse;
import az.millers.hcm.letters.api.dto.LetterSubmitRequest;
import az.millers.hcm.letters.domain.LetterStatus;
import az.millers.hcm.letters.repo.LetterTemplateRepository;
import az.millers.hcm.letters.service.LetterRequestService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/letter-requests")
public class LetterRequestController {

    private final LetterRequestService service;
    private final LetterTemplateRepository templateRepo;

    public LetterRequestController(LetterRequestService service,
                                    LetterTemplateRepository templateRepo) {
        this.service = service;
        this.templateRepo = templateRepo;
    }

    /**
     * HR / scoped-manager queue. Scope filter is applied inside the service
     * so DEPARTMENT_MANAGER callers only see their team's requests.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','DEPARTMENT_MANAGER','AUDITOR')")
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
    @PreAuthorize("hasAnyRole('HR_ADMIN','HR_SPECIALIST','SYSTEM_ADMIN')")
    public LetterRequestResponse create(@Valid @RequestBody LetterSubmitRequest req) {
        return LetterRequestResponse.from(service.submit(req));
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
}
