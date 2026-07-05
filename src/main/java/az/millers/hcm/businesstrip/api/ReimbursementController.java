package az.millers.hcm.businesstrip.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import az.millers.hcm.businesstrip.domain.ReimbursementBatch;
import az.millers.hcm.businesstrip.domain.ReimbursementBatchItem;
import az.millers.hcm.businesstrip.domain.ReimbursementBatchStatus;
import az.millers.hcm.businesstrip.service.ReimbursementService;

/**
 * Expense reimbursement batches (M455).
 */
@RestController
@RequestMapping("/api/business-trips/reimbursement-batches")
public class ReimbursementController {

    private final ReimbursementService service;

    public ReimbursementController(ReimbursementService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('WRITE_HR')")
    public ReimbursementBatchResponse createBatch(@RequestBody CreateBatchRequest req) {
        ReimbursementBatch batch = service.createBatch(req.claimIds());
        return toResponse(batch);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('WRITE_HR_ADMIN')")
    public ReimbursementBatchResponse approve(@PathVariable UUID id) {
        return toResponse(service.approve(id));
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize("hasAuthority('WRITE_HR_ADMIN')")
    public ReimbursementBatchResponse markPaid(@PathVariable UUID id, @RequestBody(required = false) PayRequest req) {
        UUID payrollRunId = req != null ? req.payrollRunId() : null;
        String paymentRef = req != null ? req.paymentRef() : null;
        return toResponse(service.markPaid(id, payrollRunId, paymentRef));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('READ_HR')")
    public ReimbursementBatchResponse get(@PathVariable UUID id) {
        ReimbursementBatch batch = service.get(id);
        List<ReimbursementBatchItem> items = service.getBatchItems(id);
        return toResponse(batch, items);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('READ_HR')")
    public List<ReimbursementBatchResponse> list(
            @RequestParam(required = false) ReimbursementBatchStatus status) {
        return service.list(status).stream().map(this::toResponse).toList();
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    private ReimbursementBatchResponse toResponse(ReimbursementBatch b) {
        List<ReimbursementBatchItem> items = service.getBatchItems(b.getId());
        return toResponse(b, items);
    }

    private ReimbursementBatchResponse toResponse(ReimbursementBatch b, List<ReimbursementBatchItem> items) {
        List<BatchItemResponse> itemDtos = items.stream()
                .map(i -> new BatchItemResponse(i.getId(), i.getExpenseClaimId(), i.getAmount()))
                .toList();
        return new ReimbursementBatchResponse(
                b.getId(), b.getBatchNo(), b.getStatus(), b.getTotalAmount(), b.getCurrency(),
                b.getCreatedAt(), b.getCreatedBy(), b.getApprovedBy(), b.getApprovedAt(),
                b.getPaidAt(), b.getPaymentRef(), itemDtos);
    }

    public record CreateBatchRequest(List<UUID> claimIds) {
    }

    public record PayRequest(UUID payrollRunId, String paymentRef) {
    }

    public record ReimbursementBatchResponse(
            UUID id,
            String batchNo,
            ReimbursementBatchStatus status,
            BigDecimal totalAmount,
            String currency,
            OffsetDateTime createdAt,
            String createdBy,
            String approvedBy,
            OffsetDateTime approvedAt,
            OffsetDateTime paidAt,
            String paymentRef,
            List<BatchItemResponse> items) {
    }

    public record BatchItemResponse(
            UUID id,
            UUID expenseClaimId,
            BigDecimal amount) {
    }
}
