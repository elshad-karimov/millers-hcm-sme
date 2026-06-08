package az.millers.hcm.payroll.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.payroll.api.dto.ErpExportDtos.ErpExportResponse;
import az.millers.hcm.payroll.api.dto.ErpExportDtos.ErpGenerateRequest;
import az.millers.hcm.payroll.service.ErpExportService;
import az.millers.hcm.security.SecurityRoles;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/payroll")
public class ErpExportController {

    private final ErpExportService service;

    public ErpExportController(ErpExportService service) {
        this.service = service;
    }

    /** List all ERP exports across all runs. */
    @GetMapping("/erp-exports")
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public List<ErpExportResponse> listAll() {
        return service.listAll();
    }

    /** List ERP exports for a specific payroll run. */
    @GetMapping("/runs/{runId}/erp-exports")
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public List<ErpExportResponse> listByRun(@PathVariable UUID runId) {
        return service.listByRun(runId);
    }

    /** Get a single export with journal lines. */
    @GetMapping("/erp-exports/{id}")
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public ErpExportResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    /** Generate a new journal export batch from a payroll run. */
    @PostMapping("/runs/{runId}/erp-exports")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public ErpExportResponse generate(@PathVariable UUID runId,
                                      @Valid @RequestBody ErpGenerateRequest req) {
        return service.generate(runId, req);
    }

    /** Download the export file (CSV or JSON). */
    @GetMapping("/erp-exports/{id}/download")
    @PreAuthorize(SecurityRoles.READ_PAYROLL)
    public ResponseEntity<byte[]> download(@PathVariable UUID id) {
        ErpExportResponse meta = service.get(id);
        byte[] bytes = service.download(id);

        String filename = meta.exportNo() + "." + fileExtension(meta.format());
        MediaType mediaType = meta.format().equals("JSON") ? MediaType.APPLICATION_JSON
                : MediaType.parseMediaType("text/csv");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(
                ContentDisposition.attachment().filename(filename).build());
        headers.setContentType(mediaType);
        headers.setContentLength(bytes.length);

        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    private static String fileExtension(String format) {
        return "JSON".equals(format) ? "json" : "csv";
    }
}
