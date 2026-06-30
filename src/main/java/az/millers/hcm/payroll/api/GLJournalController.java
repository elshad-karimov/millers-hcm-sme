package az.millers.hcm.payroll.api;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.payroll.domain.GLJournal;
import az.millers.hcm.payroll.service.GLJournalService;
import az.millers.hcm.security.SecurityRoles;

/**
 * M355 — GL journal endpoints.
 */
@RestController
@RequestMapping("/api/payroll/runs/{runId}/gl-journal")
public class GLJournalController {

    private final GLJournalService service;

    public GLJournalController(GLJournalService service) {
        this.service = service;
    }

    @PostMapping("/generate")
    @PreAuthorize(SecurityRoles.WRITE_PAYROLL)
    public ResponseEntity<GLJournal> generate(@PathVariable UUID runId) {
        GLJournal journal = service.generate(runId);
        return ResponseEntity.ok(journal);
    }

    @GetMapping
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public ResponseEntity<GLJournalService.JournalResponse> get(@PathVariable UUID runId) {
        return ResponseEntity.ok(service.get(runId));
    }

    @GetMapping("/export")
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public ResponseEntity<byte[]> export(@PathVariable UUID runId) {
        byte[] csv = service.exportCsv(runId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=gl_journal_" + runId + ".csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }
}
