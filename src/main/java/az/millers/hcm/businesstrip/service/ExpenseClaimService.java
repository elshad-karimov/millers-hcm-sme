package az.millers.hcm.businesstrip.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.businesstrip.api.dto.ExpenseClaimDtos.ClaimResponse;
import az.millers.hcm.businesstrip.api.dto.ExpenseClaimDtos.CreateClaimRequest;
import az.millers.hcm.businesstrip.api.dto.ExpenseClaimDtos.ItemRequest;
import az.millers.hcm.businesstrip.api.dto.ExpenseClaimDtos.ItemResponse;
import az.millers.hcm.businesstrip.api.dto.ExpenseClaimDtos.RejectRequest;
import az.millers.hcm.businesstrip.domain.BusinessTripRequest;
import az.millers.hcm.businesstrip.domain.ClaimStatus;
import az.millers.hcm.businesstrip.domain.ExpenseClaim;
import az.millers.hcm.businesstrip.domain.ExpenseItem;
import az.millers.hcm.businesstrip.repo.BusinessTripRequestRepository;
import az.millers.hcm.businesstrip.repo.ExpenseClaimRepository;
import az.millers.hcm.businesstrip.repo.ExpenseItemRepository;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * Expense claim lifecycle (M104).
 *
 * <p>DRAFT → SUBMITTED (employee) → APPROVED/REJECTED (HR) → PAID (HR admin).
 * Items can be added/removed freely while in DRAFT; not after submission.
 */
@Service
public class ExpenseClaimService {

    private static final String MODULE = "BUSINESS_TRIP";
    private static final String ENTITY = "ExpenseClaim";

    private final ExpenseClaimRepository claims;
    private final ExpenseItemRepository items;
    private final BusinessTripRequestRepository trips;
    private final EmployeeRepository employees;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public ExpenseClaimService(ExpenseClaimRepository claims,
                                ExpenseItemRepository items,
                                BusinessTripRequestRepository trips,
                                EmployeeRepository employees,
                                AuditService audit,
                                CurrentRequest currentRequest) {
        this.claims = claims;
        this.items = items;
        this.trips = trips;
        this.employees = employees;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    // ── Commands ─────────────────────────────────────────────────────────────

    @Transactional
    public ClaimResponse create(CreateClaimRequest req) {
        BusinessTripRequest trip = trips.findById(req.tripId())
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found: " + req.tripId()));
        ExpenseClaim claim = new ExpenseClaim();
        claim.setTripId(req.tripId());
        claim.setEmployeeId(trip.getEmployeeId());
        claim.setCurrency(req.currency() != null ? req.currency() : trip.getCurrency());
        claim.setNotes(req.notes());
        claim.setCreatedBy(currentRequest.username());
        claims.save(claim);

        List<ExpenseItem> savedItems = saveItems(claim.getId(), req.items());
        audit.record(MODULE, ENTITY, claim.getId().toString(), "CREATE", null,
                Map.of("tripId", req.tripId().toString(), "itemCount",
                        savedItems.size()));
        return toResponse(claim, trip, savedItems);
    }

    @Transactional
    public ClaimResponse submit(UUID claimId) {
        ExpenseClaim claim = mustFind(claimId);
        if (claim.getStatus() != ClaimStatus.DRAFT) {
            throw new BadRequestException("Only DRAFT claims can be submitted");
        }
        List<ExpenseItem> claimItems = items.findByClaimIdOrderByItemDateAscCategoryAsc(claimId);
        if (claimItems.isEmpty()) {
            throw new BadRequestException("Cannot submit a claim with no items");
        }
        claim.setStatus(ClaimStatus.SUBMITTED);
        claim.setSubmittedAt(OffsetDateTime.now());
        claims.save(claim);
        audit.record(MODULE, ENTITY, claimId.toString(), "SUBMIT", null,
                Map.of("total", total(claimItems).toString()));
        return toResponse(claim);
    }

    @Transactional
    public ClaimResponse approve(UUID claimId) {
        ExpenseClaim claim = mustFind(claimId);
        if (claim.getStatus() != ClaimStatus.SUBMITTED) {
            throw new BadRequestException("Only SUBMITTED claims can be approved");
        }
        claim.setStatus(ClaimStatus.APPROVED);
        claim.setApprovedAt(OffsetDateTime.now());
        claim.setApprovedBy(currentRequest.username());
        claims.save(claim);
        audit.record(MODULE, ENTITY, claimId.toString(), "APPROVE", null, Map.of());
        return toResponse(claim);
    }

    @Transactional
    public ClaimResponse reject(UUID claimId, RejectRequest req) {
        ExpenseClaim claim = mustFind(claimId);
        if (claim.getStatus() != ClaimStatus.SUBMITTED) {
            throw new BadRequestException("Only SUBMITTED claims can be rejected");
        }
        claim.setStatus(ClaimStatus.REJECTED);
        claim.setRejectedAt(OffsetDateTime.now());
        claim.setRejectionReason(req == null ? null : req.reason());
        claims.save(claim);
        audit.record(MODULE, ENTITY, claimId.toString(), "REJECT", null,
                Map.of("reason", req == null || req.reason() == null ? "" : req.reason()));
        return toResponse(claim);
    }

    @Transactional
    public ClaimResponse markPaid(UUID claimId) {
        ExpenseClaim claim = mustFind(claimId);
        if (claim.getStatus() != ClaimStatus.APPROVED) {
            throw new BadRequestException("Only APPROVED claims can be marked paid");
        }
        claim.setStatus(ClaimStatus.PAID);
        claim.setPaidAt(OffsetDateTime.now());
        claims.save(claim);
        audit.record(MODULE, ENTITY, claimId.toString(), "PAID", null, Map.of());
        return toResponse(claim);
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ClaimResponse get(UUID claimId) {
        return toResponse(mustFind(claimId));
    }

    @Transactional(readOnly = true)
    public List<ClaimResponse> forEmployee(UUID employeeId) {
        return claims.findByEmployeeIdOrderByCreatedAtDesc(employeeId).stream()
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ClaimResponse> forTrip(UUID tripId) {
        return claims.findByTripIdOrderByCreatedAtDesc(tripId).stream()
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ClaimResponse> byStatus(ClaimStatus status) {
        List<ExpenseClaim> found = status != null
                ? claims.findByStatusOrderByCreatedAtDesc(status)
                : claims.findAll();
        return found.stream().map(this::toResponse).toList();
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private List<ExpenseItem> saveItems(UUID claimId, List<ItemRequest> reqs) {
        if (reqs == null || reqs.isEmpty()) return List.of();
        return reqs.stream().map(r -> {
            if (r.category() == null) throw new BadRequestException("Item category is required");
            if (r.amount() == null || r.amount().compareTo(BigDecimal.ZERO) < 0) {
                throw new BadRequestException("Item amount must be non-negative");
            }
            ExpenseItem item = new ExpenseItem();
            item.setClaimId(claimId);
            item.setCategory(r.category());
            item.setDescription(r.description());
            item.setAmount(r.amount());
            item.setItemDate(r.itemDate());
            item.setReceiptUrl(r.receiptUrl());
            return items.save(item);
        }).toList();
    }

    private ClaimResponse toResponse(ExpenseClaim c) {
        List<ExpenseItem> claimItems =
                items.findByClaimIdOrderByItemDateAscCategoryAsc(c.getId());
        BusinessTripRequest trip = trips.findById(c.getTripId()).orElse(null);
        return toResponse(c, trip, claimItems);
    }

    private ClaimResponse toResponse(ExpenseClaim c, BusinessTripRequest trip,
                                      List<ExpenseItem> claimItems) {
        Optional<Employee> emp = employees.findById(c.getEmployeeId());
        String empName = emp.map(e ->
                ((e.getFirstName() == null ? "" : e.getFirstName()) + " "
                        + (e.getLastName() == null ? "" : e.getLastName())).trim())
                .orElse(null);
        List<ItemResponse> itemDtos = claimItems.stream()
                .map(i -> new ItemResponse(i.getId(), i.getCategory(), i.getDescription(),
                        i.getAmount(), i.getItemDate(), i.getReceiptUrl()))
                .toList();
        return new ClaimResponse(
                c.getId(), c.getClaimNo(),
                c.getTripId(),
                trip == null ? null : trip.getTripNo(),
                trip == null ? null : trip.getDestinationCity(),
                c.getEmployeeId(), empName,
                c.getCurrency(), c.getStatus(),
                total(claimItems),
                c.getSubmittedAt(), c.getApprovedAt(), c.getApprovedBy(),
                c.getRejectedAt(), c.getRejectionReason(),
                c.getPaidAt(), c.getNotes(), c.getCreatedAt(),
                itemDtos);
    }

    /** Package-private for tests. */
    static BigDecimal total(List<ExpenseItem> claimItems) {
        return claimItems.stream()
                .map(ExpenseItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private ExpenseClaim mustFind(UUID id) {
        return claims.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense claim not found: " + id));
    }
}
