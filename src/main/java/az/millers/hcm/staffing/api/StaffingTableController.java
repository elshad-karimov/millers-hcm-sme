package az.millers.hcm.staffing.api;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.security.SecurityRoles;
import az.millers.hcm.staffing.api.dto.StaffingTableDtos.DiffResponse;
import az.millers.hcm.staffing.api.dto.StaffingTableDtos.LineRequest;
import az.millers.hcm.staffing.api.dto.StaffingTableDtos.LineResponse;
import az.millers.hcm.staffing.api.dto.StaffingTableDtos.RejectRequest;
import az.millers.hcm.staffing.api.dto.StaffingTableDtos.TableHeaderRequest;
import az.millers.hcm.staffing.api.dto.StaffingTableDtos.TableResponse;
import az.millers.hcm.staffing.service.StaffingTableService;
import az.millers.hcm.staffing.service.StaffingTableXlsxExport;
import jakarta.validation.Valid;

/**
 * M245 — REST surface for the staffing table (ştat cədvəli).
 *
 * Read: HR / Finance / Auditor. Write: HR_ADMIN (and FINANCE_USER for
 * the budget-heavy approve action).
 */
@RestController
@RequestMapping("/api/staffing-tables")
public class StaffingTableController {

    private final StaffingTableService service;
    private final StaffingTableXlsxExport xlsx;

    public StaffingTableController(StaffingTableService service,
                                    StaffingTableXlsxExport xlsx) {
        this.service = service;
        this.xlsx = xlsx;
    }

    // ── Headers ─────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','FINANCE_USER')")
    public List<TableResponse> list(@RequestParam(required = false) UUID legalEntityId) {
        var rows = legalEntityId == null
                ? service.listAll()
                : service.listForLegalEntity(legalEntityId);
        return rows.stream()
                .map(t -> TableResponse.from(t, service.linesFor(t.getId())))
                .toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','FINANCE_USER')")
    public TableResponse get(@PathVariable UUID id) {
        var t = service.get(id);
        return TableResponse.from(t, service.linesFor(id));
    }

    @PostMapping
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public TableResponse create(@Valid @RequestBody TableHeaderRequest req) {
        return TableResponse.from(service.create(req.toEntity()));
    }

    @PutMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public TableResponse update(@PathVariable UUID id,
                                 @Valid @RequestBody TableHeaderRequest req) {
        return TableResponse.from(service.update(id, req.toEntity()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    // ── Lines ───────────────────────────────────────────────────────

    @GetMapping("/{id}/lines")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','FINANCE_USER')")
    public List<LineResponse> lines(@PathVariable UUID id) {
        return service.linesFor(id).stream().map(LineResponse::from).toList();
    }

    @PostMapping("/{id}/lines")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public LineResponse addLine(@PathVariable UUID id, @Valid @RequestBody LineRequest req) {
        return LineResponse.from(service.addLine(id, req.toEntity()));
    }

    @PutMapping("/lines/{lineId}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public LineResponse updateLine(@PathVariable UUID lineId, @Valid @RequestBody LineRequest req) {
        return LineResponse.from(service.updateLine(lineId, req.toEntity()));
    }

    @DeleteMapping("/lines/{lineId}")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public void deleteLine(@PathVariable UUID lineId) {
        service.deleteLine(lineId);
    }

    // ── Generate from live positions ────────────────────────────────

    @PostMapping("/{id}/generate-from-positions")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public List<LineResponse> generateFromPositions(@PathVariable UUID id) {
        return service.generateFromPositions(id).stream()
                .map(LineResponse::from).toList();
    }

    // ── Lifecycle ───────────────────────────────────────────────────

    @PostMapping("/{id}/submit")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public TableResponse submit(@PathVariable UUID id) {
        return TableResponse.from(service.submit(id));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('HR_ADMIN','FINANCE_USER')")
    public TableResponse approve(@PathVariable UUID id) {
        return TableResponse.from(service.approve(id));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('HR_ADMIN','FINANCE_USER')")
    public TableResponse reject(@PathVariable UUID id,
                                 @Valid @RequestBody RejectRequest req) {
        return TableResponse.from(service.reject(id, req.reason()));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize(SecurityRoles.WRITE_HR_ADMIN_ONLY)
    public TableResponse archive(@PathVariable UUID id) {
        return TableResponse.from(service.archive(id));
    }

    // ── Diff ────────────────────────────────────────────────────────

    @GetMapping("/{idA}/compare/{idB}")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','FINANCE_USER')")
    public DiffResponse compare(@PathVariable UUID idA, @PathVariable UUID idB) {
        return DiffResponse.from(service.compare(idA, idB));
    }

    // ── Excel export ────────────────────────────────────────────────

    @GetMapping(value = "/{id}/export/xlsx")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST','AUDITOR','FINANCE_USER')")
    public ResponseEntity<ByteArrayResource> exportXlsx(@PathVariable UUID id) throws IOException {
        byte[] bytes = xlsx.render(id);
        var t = service.get(id);
        String filename = "stat-cedveli-" + t.getVersionCode() + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(bytes.length)
                .body(new ByteArrayResource(bytes));
    }
}
