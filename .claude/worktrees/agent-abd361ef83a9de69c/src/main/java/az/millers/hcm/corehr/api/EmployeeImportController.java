package az.millers.hcm.corehr.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import az.millers.hcm.common.PageResponse;
import az.millers.hcm.corehr.api.dto.ImportJobResponse;
import az.millers.hcm.corehr.service.EmployeeImportService;

/**
 * Bulk employee import endpoints (M69 / P1-16).
 *
 * <p>Lives in its own controller so the standard CRUD surface in
 * {@link EmployeeController} stays focused. Both endpoints are gated to
 * HR_ADMIN / SYSTEM_ADMIN — bulk import bypasses the per-row request
 * validation flow and is a high-trust operation.
 */
@RestController
@RequestMapping("/api/employees/import")
public class EmployeeImportController {

    private static final String ROLES = "hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')";

    private final EmployeeImportService importService;

    public EmployeeImportController(EmployeeImportService importService) {
        this.importService = importService;
    }

    /**
     * Upload a workbook for bulk import.
     *
     * @param file    the {@code .xlsx} / {@code .xls} file (required column
     *                headers: firstName, lastName, hireDate)
     * @param dryRun  when {@code true}, validation runs but no rows are
     *                committed — the response still contains the per-row
     *                error report so HR can fix the spreadsheet before
     *                committing.
     */
    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize(ROLES)
    public ImportJobResponse importEmployees(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean dryRun) {
        return importService.importFile(file, dryRun);
    }

    /** Recent import jobs, newest first. */
    @GetMapping("/history")
    @PreAuthorize(ROLES)
    public PageResponse<ImportJobResponse> history(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt"));
        Page<ImportJobResponse> result = importService.history(pageable);
        return PageResponse.of(result, j -> j);
    }
}
