package az.millers.hcm.compbenefits.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.compbenefits.api.dto.BenefitClaimDtos.ClaimRequest;
import az.millers.hcm.compbenefits.api.dto.BenefitClaimDtos.ClaimResponse;
import az.millers.hcm.compbenefits.api.dto.BenefitClaimDtos.ItemRequest;
import az.millers.hcm.compbenefits.domain.BenefitClaim;
import az.millers.hcm.compbenefits.domain.BenefitClaimItem;
import az.millers.hcm.compbenefits.domain.BenefitClaimStatus;
import az.millers.hcm.compbenefits.domain.BenefitPlan;
import az.millers.hcm.compbenefits.repo.BenefitClaimItemRepository;
import az.millers.hcm.compbenefits.repo.BenefitClaimRepository;
import az.millers.hcm.compbenefits.repo.BenefitPlanRepository;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.notifications.NotificationService;
import az.millers.hcm.notifications.domain.NotificationCategory;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.scope.AccessScopeService;

/**
 * HCM_11 M381/M382 — benefit reimbursement claims. Mirrors the ExpenseClaim state machine
 * (DRAFT→SUBMITTED→APPROVED/REJECTED→PAID). Payout is tracking-only (markPaid stamps a
 * reference; NOT pushed to payroll — documented later seam).
 */
@Service
public class BenefitClaimService {

    private static final String TENANT = "default";
    private static final String MODULE = "COMP_BENEFITS";
    private static final String ENTITY = "BenefitClaim";

    private final BenefitClaimRepository claims;
    private final BenefitClaimItemRepository items;
    private final BenefitPlanRepository plans;
    private final EmployeeRepository employees;
    private final NotificationService notifications;
    private final AccessScopeService accessScope;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public BenefitClaimService(BenefitClaimRepository claims,
                               BenefitClaimItemRepository items,
                               BenefitPlanRepository plans,
                               EmployeeRepository employees,
                               NotificationService notifications,
                               AccessScopeService accessScope,
                               AuditService audit,
                               CurrentRequest currentRequest) {
        this.claims = claims;
        this.items = items;
        this.plans = plans;
        this.employees = employees;
        this.notifications = notifications;
        this.accessScope = accessScope;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    /** Non-fatal notification to the claim's employee about a decision. */
    private void notifyEmployee(BenefitClaim c, String title, String body) {
        try {
            Employee emp = employees.findById(c.getEmployeeId()).orElse(null);
            if (emp != null && emp.getUsername() != null && !emp.getUsername().isBlank()) {
                notifications.notifyAll(NotificationCategory.BENEFIT_ENROLLMENT, emp.getUsername(),
                        title, body, MODULE, ENTITY, c.getId().toString());
            }
        } catch (Exception ex) {
            // best-effort
        }
    }

    // ── Reads ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ClaimResponse> list(BenefitClaimStatus status) {
        List<BenefitClaim> rows = status == null
                ? claims.findByTenantIdOrderByClaimDateDesc(TENANT)
                : claims.findByTenantIdAndStatusOrderByClaimDateDesc(TENANT, status);
        return decorate(rows);
    }

    @Transactional(readOnly = true)
    public List<ClaimResponse> listForEmployee(UUID employeeId) {
        // Hierarchy scope (GLOBAL RULE 7): managers only see their reports; HR/admin unrestricted.
        if (!accessScope.isAccessible(employeeId)) {
            return List.of();
        }
        return decorate(claims.findByTenantIdAndEmployeeIdOrderByClaimDateDesc(TENANT, employeeId));
    }

    @Transactional(readOnly = true)
    public ClaimResponse get(UUID id) {
        return decorate(List.of(load(id))).get(0);
    }

    private BenefitClaim load(UUID id) {
        return claims.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Benefit claim not found: " + id));
    }

    private List<ClaimResponse> decorate(List<BenefitClaim> rows) {
        if (rows.isEmpty()) return List.of();
        Map<UUID, String> empNames = new HashMap<>();
        Map<UUID, String> planNames = new HashMap<>();
        Map<UUID, List<BenefitClaimItem>> itemsByClaim = new HashMap<>();
        for (BenefitClaimItem it : items.findByClaimIdIn(rows.stream().map(BenefitClaim::getId).toList())) {
            itemsByClaim.computeIfAbsent(it.getClaimId(), k -> new ArrayList<>()).add(it);
        }
        return rows.stream().map(c -> ClaimResponse.from(c,
                empNames.computeIfAbsent(c.getEmployeeId(), this::employeeName),
                c.getPlanId() == null ? null : planNames.computeIfAbsent(c.getPlanId(), this::planName),
                itemsByClaim.getOrDefault(c.getId(), List.of()))).toList();
    }

    private String employeeName(UUID empId) {
        return employees.findById(empId)
                .map(e -> (e.getFirstName() + " " + e.getLastName()).trim()).orElse(null);
    }

    private String planName(UUID planId) {
        return plans.findById(planId).map(BenefitPlan::getName).orElse(null);
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    @Transactional
    public ClaimResponse create(ClaimRequest req) {
        if (!employees.existsById(req.employeeId())) {
            throw new BadRequestException("Employee not found: " + req.employeeId());
        }
        if (req.items() == null || req.items().isEmpty()) {
            throw new BadRequestException("A claim needs at least one line item");
        }
        BenefitClaim c = new BenefitClaim();
        c.setTenantId(TENANT);
        c.setClaimNo(String.format("BC-%05d", claims.nextClaimSeq()));
        c.setEmployeeId(req.employeeId());
        c.setEnrollmentId(req.enrollmentId());
        c.setPlanId(req.planId());
        c.setClaimDate(req.claimDate());
        c.setCurrency(req.currency() == null || req.currency().isBlank() ? "AZN" : req.currency().toUpperCase());
        c.setDescription(req.description());
        c.setStatus(BenefitClaimStatus.DRAFT);
        c.setTotalAmount(req.items().stream()
                .map(i -> i.amount() == null ? BigDecimal.ZERO : i.amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        BenefitClaim saved = claims.save(c);
        for (ItemRequest ir : req.items()) {
            BenefitClaimItem it = new BenefitClaimItem();
            it.setClaimId(saved.getId());
            it.setServiceDate(ir.serviceDate());
            it.setDescription(ir.description());
            it.setAmount(ir.amount() == null ? BigDecimal.ZERO : ir.amount());
            items.save(it);
        }
        audit.record(MODULE, ENTITY, saved.getId().toString(), "CREATE", null, saved.getClaimNo());
        return get(saved.getId());
    }

    @Transactional
    public ClaimResponse submit(UUID id) {
        BenefitClaim c = load(id);
        require(c, BenefitClaimStatus.DRAFT, "submitted");
        c.setStatus(BenefitClaimStatus.SUBMITTED);
        c.setSubmittedBy(currentRequest.username());
        c.setSubmittedAt(OffsetDateTime.now());
        claims.save(c);
        audit.record(MODULE, ENTITY, id.toString(), "SUBMIT", null, c.getClaimNo());
        return get(id);
    }

    @Transactional
    public ClaimResponse approve(UUID id, BigDecimal approvedAmount, String notes) {
        BenefitClaim c = load(id);
        require(c, BenefitClaimStatus.SUBMITTED, "approved");
        c.setStatus(BenefitClaimStatus.APPROVED);
        c.setApprovedAmount(approvedAmount != null ? approvedAmount : c.getTotalAmount());
        c.setReviewedBy(currentRequest.username());
        c.setReviewedAt(OffsetDateTime.now());
        c.setReviewNotes(notes);
        claims.save(c);
        audit.record(MODULE, ENTITY, id.toString(), "APPROVE", null, c.getApprovedAmount());
        notifyEmployee(c, "Benefit claim approved: " + c.getClaimNo(),
                "Your benefit claim " + c.getClaimNo() + " was approved for "
                        + c.getApprovedAmount() + " " + c.getCurrency() + ".");
        return get(id);
    }

    @Transactional
    public ClaimResponse reject(UUID id, String notes) {
        BenefitClaim c = load(id);
        require(c, BenefitClaimStatus.SUBMITTED, "rejected");
        c.setStatus(BenefitClaimStatus.REJECTED);
        c.setReviewedBy(currentRequest.username());
        c.setReviewedAt(OffsetDateTime.now());
        c.setReviewNotes(notes);
        claims.save(c);
        audit.record(MODULE, ENTITY, id.toString(), "REJECT", null, notes);
        notifyEmployee(c, "Benefit claim rejected: " + c.getClaimNo(),
                "Your benefit claim " + c.getClaimNo() + " was rejected"
                        + (notes == null || notes.isBlank() ? "." : ": " + notes));
        return get(id);
    }

    @Transactional
    public ClaimResponse markPaid(UUID id, String paymentReference) {
        BenefitClaim c = load(id);
        require(c, BenefitClaimStatus.APPROVED, "marked paid");
        c.setStatus(BenefitClaimStatus.PAID);
        c.setPaidBy(currentRequest.username());
        c.setPaidAt(OffsetDateTime.now());
        c.setPaymentReference(paymentReference);
        claims.save(c);
        audit.record(MODULE, ENTITY, id.toString(), "MARK_PAID", null, paymentReference);
        return get(id);
    }

    @Transactional
    public ClaimResponse cancel(UUID id) {
        BenefitClaim c = load(id);
        if (c.getStatus() != BenefitClaimStatus.DRAFT && c.getStatus() != BenefitClaimStatus.SUBMITTED) {
            throw new BadRequestException("Only DRAFT or SUBMITTED claims can be cancelled");
        }
        c.setStatus(BenefitClaimStatus.CANCELLED);
        claims.save(c);
        audit.record(MODULE, ENTITY, id.toString(), "CANCEL", null, c.getClaimNo());
        return get(id);
    }

    private static void require(BenefitClaim c, BenefitClaimStatus expected, String action) {
        if (c.getStatus() != expected) {
            throw new BadRequestException("Claim must be " + expected + " to be " + action
                    + " (current: " + c.getStatus() + ")");
        }
    }
}
