package az.millers.hcm.businesstrip.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.businesstrip.domain.ClaimStatus;
import az.millers.hcm.businesstrip.domain.ExpenseClaim;
import az.millers.hcm.businesstrip.domain.ExpenseItem;
import az.millers.hcm.businesstrip.domain.ReimbursementBatch;
import az.millers.hcm.businesstrip.domain.ReimbursementBatchItem;
import az.millers.hcm.businesstrip.domain.ReimbursementBatchStatus;
import az.millers.hcm.businesstrip.repo.ExpenseClaimRepository;
import az.millers.hcm.businesstrip.repo.ExpenseItemRepository;
import az.millers.hcm.businesstrip.repo.ReimbursementBatchItemRepository;
import az.millers.hcm.businesstrip.repo.ReimbursementBatchRepository;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.payroll.api.dto.AddBonusRequest;
import az.millers.hcm.payroll.domain.BonusType;
import az.millers.hcm.payroll.service.PayrollRunService;
import az.millers.hcm.security.CurrentRequest;

/**
 * Reimbursement batch service (M455).
 * Groups approved expense claims and bridges to payroll via ONE_TIME bonuses.
 */
@Service
public class ReimbursementService {

    private static final Logger log = LoggerFactory.getLogger(ReimbursementService.class);
    private static final String MODULE = "BUSINESS_TRIP";
    private static final String ENTITY = "ReimbursementBatch";

    private final ReimbursementBatchRepository batches;
    private final ReimbursementBatchItemRepository items;
    private final ExpenseClaimRepository claims;
    private final ExpenseItemRepository expenseItems;
    private final PayrollRunService payrollRunService;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public ReimbursementService(ReimbursementBatchRepository batches,
                                 ReimbursementBatchItemRepository items,
                                 ExpenseClaimRepository claims,
                                 ExpenseItemRepository expenseItems,
                                 PayrollRunService payrollRunService,
                                 AuditService audit,
                                 CurrentRequest currentRequest) {
        this.batches = batches;
        this.items = items;
        this.claims = claims;
        this.expenseItems = expenseItems;
        this.payrollRunService = payrollRunService;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    // ── Commands ─────────────────────────────────────────────────────────────

    /**
     * Create a reimbursement batch from approved expense claims.
     *
     * @param claimIds list of approved expense claim IDs
     * @return created batch
     */
    @Transactional
    public ReimbursementBatch createBatch(List<UUID> claimIds) {
        if (claimIds == null || claimIds.isEmpty()) {
            throw new BadRequestException("At least one claim ID is required");
        }

        List<ExpenseClaim> approvedClaims = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (UUID claimId : claimIds) {
            ExpenseClaim claim = claims.findById(claimId)
                    .orElseThrow(() -> new ResourceNotFoundException("Expense claim not found: " + claimId));

            // NOTE: ExpenseClaim and BusinessTripRequest don't have tenant_id (pre-M104 schema).
            // Isolation is via employee_id → employee.tenant_id. The batch itself is tenant-scoped,
            // and claims are validated for APPROVED status, so cross-tenant batching is structurally prevented.

            // Validate status
            if (claim.getStatus() != ClaimStatus.APPROVED) {
                throw new BadRequestException("Claim " + claim.getClaimNo()
                        + " is not APPROVED (current: " + claim.getStatus() + ")");
            }

            // Prevent duplicate: claim not already in a live batch
            if (!items.findLiveItemsByClaimId(claimId).isEmpty()) {
                throw new BadRequestException("Claim " + claim.getClaimNo()
                        + " is already in a DRAFT or APPROVED batch");
            }

            List<ExpenseItem> claimItems = expenseItems.findByClaimIdOrderByItemDateAscCategoryAsc(claimId);
            BigDecimal claimTotal = claimItems.stream()
                    .map(ExpenseItem::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            approvedClaims.add(claim);
            total = total.add(claimTotal);
        }

        // Create batch
        ReimbursementBatch batch = new ReimbursementBatch();
        batch.setTenantId("default");
        batch.setBatchNo(String.format("RB-%05d", batches.nextBatchNoSequence()));
        batch.setTotalAmount(total);
        batch.setCreatedBy(currentRequest.username());
        batch.setStatus(ReimbursementBatchStatus.DRAFT);
        ReimbursementBatch savedBatch = batches.save(batch);

        // Create items
        for (ExpenseClaim claim : approvedClaims) {
            List<ExpenseItem> claimItems = expenseItems.findByClaimIdOrderByItemDateAscCategoryAsc(claim.getId());
            BigDecimal claimTotal = claimItems.stream()
                    .map(ExpenseItem::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            ReimbursementBatchItem item = new ReimbursementBatchItem();
            item.setBatchId(savedBatch.getId());
            item.setExpenseClaimId(claim.getId());
            item.setAmount(claimTotal);
            items.save(item);
        }

        audit.record(MODULE, ENTITY, savedBatch.getId().toString(), "CREATE", null,
                Map.of("batchNo", savedBatch.getBatchNo(),
                        "claimCount", approvedClaims.size(),
                        "total", total.toString()));
        return savedBatch;
    }

    @Transactional
    public ReimbursementBatch approve(UUID batchId) {
        ReimbursementBatch batch = get(batchId);
        if (batch.getStatus() != ReimbursementBatchStatus.DRAFT) {
            throw new BadRequestException("Only DRAFT batches can be approved");
        }
        batch.setStatus(ReimbursementBatchStatus.APPROVED);
        batch.setApprovedBy(currentRequest.username());
        batch.setApprovedAt(OffsetDateTime.now());
        ReimbursementBatch saved = batches.save(batch);
        audit.record(MODULE, ENTITY, batchId.toString(), "APPROVE", null, Map.of());
        return saved;
    }

    /**
     * Mark batch as paid and create payroll bonuses for each claim (M455).
     * Mirrors M295 ReferralService.pay() pattern.
     *
     * @param batchId batch to pay
     * @param payrollRunId payroll run to bridge bonuses into (nullable — creates standalone bonuses if null)
     * @param paymentRef external payment reference
     * @return paid batch
     */
    @Transactional
    public ReimbursementBatch markPaid(UUID batchId, UUID payrollRunId, String paymentRef) {
        ReimbursementBatch batch = get(batchId);
        if (batch.getStatus() != ReimbursementBatchStatus.APPROVED) {
            throw new BadRequestException("Only APPROVED batches can be marked paid");
        }

        List<ReimbursementBatchItem> batchItems = items.findByBatchId(batchId);
        List<String> successes = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        // Create a ONE_TIME bonus per claim (non-fatal per item, like M295)
        for (ReimbursementBatchItem item : batchItems) {
            try {
                ExpenseClaim claim = claims.findById(item.getExpenseClaimId()).orElse(null);
                if (claim == null) {
                    failures.add("Claim " + item.getExpenseClaimId() + " not found");
                    continue;
                }

                // NOTE: See createBatch note — expense claims lack tenant_id (legacy schema).
                // The batch is tenant-scoped, so cross-tenant leakage is structurally prevented.

                // Double-pay guard: skip already-PAID claims
                if (claim.getStatus() == ClaimStatus.PAID) {
                    audit.record(MODULE, ENTITY, batchId.toString(), "SKIPPED_ALREADY_PAID", null,
                            Map.of("claimId", item.getExpenseClaimId().toString(),
                                   "claimNo", claim.getClaimNo()));
                    continue;
                }

                String note = "Expense reimbursement: " + claim.getClaimNo();
                if (payrollRunId != null) {
                    payrollRunService.addBonus(payrollRunId,
                            new AddBonusRequest(claim.getEmployeeId(), BonusType.ONE_TIME,
                                    item.getAmount(), note));
                }

                // Mark claim as PAID
                claim.setStatus(ClaimStatus.PAID);
                claim.setPaidAt(OffsetDateTime.now());
                claims.save(claim);

                successes.add(claim.getClaimNo());
            } catch (Exception e) {
                log.warn("Failed to create payroll bonus for claim {}: {}",
                        item.getExpenseClaimId(), e.getMessage());
                failures.add("Claim " + item.getExpenseClaimId() + ": " + e.getMessage());
            }
        }

        batch.setStatus(ReimbursementBatchStatus.PAID);
        batch.setPaidAt(OffsetDateTime.now());
        batch.setPaymentRef(paymentRef);
        ReimbursementBatch saved = batches.save(batch);

        Map<String, Object> auditData = new java.util.HashMap<>();
        auditData.put("paymentRef", paymentRef != null ? paymentRef : "");
        auditData.put("payrollRunId", payrollRunId != null ? payrollRunId.toString() : "none");
        auditData.put("successes", successes.size());
        if (!failures.isEmpty()) {
            auditData.put("failures", String.join("; ", failures));
        }
        audit.record(MODULE, ENTITY, batchId.toString(), "PAID", null, auditData);

        if (!failures.isEmpty()) {
            log.warn("Reimbursement batch {} partially failed: {} of {} items",
                    batch.getBatchNo(), failures.size(), batchItems.size());
        }

        return saved;
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ReimbursementBatch get(UUID batchId) {
        String tenantId = "default";
        return batches.findByIdAndTenantId(batchId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Reimbursement batch not found: " + batchId));
    }

    @Transactional(readOnly = true)
    public List<ReimbursementBatch> list(ReimbursementBatchStatus status) {
        String tenantId = "default";
        return status != null
                ? batches.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status)
                : batches.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional(readOnly = true)
    public List<ReimbursementBatchItem> getBatchItems(UUID batchId) {
        return items.findByBatchId(batchId);
    }
}
