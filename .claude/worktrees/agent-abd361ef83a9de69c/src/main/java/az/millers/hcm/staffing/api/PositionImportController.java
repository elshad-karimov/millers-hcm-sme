package az.millers.hcm.staffing.api;

import java.io.IOException;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import az.millers.hcm.security.SecurityRoles;
import az.millers.hcm.staffing.service.PositionImportService;
import az.millers.hcm.staffing.service.PositionImportService.ImportResult;
import az.millers.hcm.staffing.service.PositionImportXlsxTemplate;

/** M255 — REST surface for bulk position import (PRD §46). */
@RestController
@RequestMapping("/api/positions/import")
public class PositionImportController {

    private final PositionImportService importer;
    private final PositionImportXlsxTemplate templateRenderer;

    public PositionImportController(PositionImportService importer,
                                     PositionImportXlsxTemplate templateRenderer) {
        this.importer = importer;
        this.templateRenderer = templateRenderer;
    }

    @GetMapping("/template")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST')")
    public ResponseEntity<ByteArrayResource> template() throws IOException {
        byte[] bytes = templateRenderer.render();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"positions-import-template.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(bytes.length)
                .body(new ByteArrayResource(bytes));
    }

    @PostMapping("/preview")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public ImportResult preview(@RequestParam("file") MultipartFile file) {
        return importer.preview(file);
    }

    @PostMapping("/commit")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public ImportResult commit(@RequestParam("file") MultipartFile file) {
        return importer.commit(file);
    }
}
