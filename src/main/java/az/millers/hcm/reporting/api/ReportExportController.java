package az.millers.hcm.reporting.api;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.reporting.domain.ReportFormat;
import az.millers.hcm.reporting.domain.ReportType;
import az.millers.hcm.reporting.service.ReportExportService;

/**
 * Synchronous download endpoint for the report aggregations defined in
 * Milestone 14. Renders to the requested format on the fly and streams
 * it back — no run history written.
 */
@RestController
@RequestMapping("/api/reports/export")
public class ReportExportController {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final ReportExportService service;

    public ReportExportController(ReportExportService service) {
        this.service = service;
    }

    @GetMapping("/{type}")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','FINANCE_USER','DEPARTMENT_MANAGER','RECRUITER')")
    public ResponseEntity<byte[]> export(
            @PathVariable ReportType type,
            @RequestParam(defaultValue = "XLSX") ReportFormat format,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID cycleId) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (year != null) params.put("year", year);
        if (from != null) params.put("from", from.toString());
        if (to != null) params.put("to", to.toString());
        if (cycleId != null) params.put("cycleId", cycleId.toString());

        byte[] payload = service.export(type, format, params);
        String filename = type.name().toLowerCase() + "-"
                + OffsetDateTime.now().format(STAMP) + "." + format.extension();
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(format.contentType()));
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        headers.setContentLength(payload.length);
        headers.add("X-Report-Filename", encoded);
        return new ResponseEntity<>(payload, headers, HttpStatus.OK);
    }
}
