package az.millers.hcm.attendance.api;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.attendance.domain.TurnstileImportBatch;
import az.millers.hcm.attendance.domain.TurnstileImportRow;
import az.millers.hcm.attendance.repo.TurnstileImportBatchRepository;
import az.millers.hcm.attendance.repo.TurnstileImportRowRepository;
import az.millers.hcm.attendance.service.AttendanceIngestService;
import az.millers.hcm.common.PageResponse;
import az.millers.hcm.common.ResourceNotFoundException;

/**
 * Manages turnstile CSV import batches (M179 / PRD §17.1).
 *
 * <p>Provides visibility into past imports and allows HR Admins to retry
 * FAILED rows without re-uploading the original file.
 */
@RestController
@RequestMapping("/api/attendance/import-batches")
@PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN','HR_SPECIALIST')")
public class TurnstileImportBatchController {

    private final TurnstileImportBatchRepository batchRepo;
    private final TurnstileImportRowRepository rowRepo;
    private final AttendanceIngestService ingestService;

    public TurnstileImportBatchController(TurnstileImportBatchRepository batchRepo,
                                          TurnstileImportRowRepository rowRepo,
                                          AttendanceIngestService ingestService) {
        this.batchRepo = batchRepo;
        this.rowRepo = rowRepo;
        this.ingestService = ingestService;
    }

    /** Lists all import batches, newest first. */
    @GetMapping
    public PageResponse<TurnstileImportBatch> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<TurnstileImportBatch> result = batchRepo.findAllByOrderByImportedAtDesc(
                PageRequest.of(page, size));
        return PageResponse.of(result, b -> b);
    }

    /** Returns a single batch header. */
    @GetMapping("/{id}")
    public TurnstileImportBatch get(@PathVariable UUID id) {
        return batchRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Import batch not found: " + id));
    }

    /** Lists the FAILED and DUPLICATE rows of a batch for review. */
    @GetMapping("/{id}/rows")
    public PageResponse<TurnstileImportRow> rows(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        batchRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Import batch not found: " + id));
        Page<TurnstileImportRow> result = rowRepo.findByBatchIdOrderByLineNumberAsc(
                id, PageRequest.of(page, size, Sort.by("lineNumber").ascending()));
        return PageResponse.of(result, r -> r);
    }

    /**
     * Retries all FAILED rows of the batch. DUPLICATE rows are not retried.
     * Returns a summary of the retry outcome.
     */
    @PostMapping("/{id}/retry-failed")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','HR_ADMIN')")
    public AttendanceIngestService.CsvImportResult retryFailed(@PathVariable UUID id) {
        return ingestService.retryFailed(id);
    }
}
