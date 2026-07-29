package az.millers.hcm.ehs.service;
import az.millers.hcm.common.tenant.TenantContext;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.ehs.domain.PpeAssignment;
import az.millers.hcm.ehs.domain.PpeItem;
import az.millers.hcm.ehs.repo.PpeAssignmentRepository;
import az.millers.hcm.ehs.repo.PpeItemRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * M451 — PPE management service.
 * Issue/return PPE to employees, track expiry.
 * Expiring PPE (within 30 days) exposed via finder for potential ExpiryAlertScheduler integration.
 */
@Service
public class PpeService {

    private static final String MODULE = "ehs";
    private static final String ENTITY_ASSIGNMENT = "PpeAssignment";

    private final PpeItemRepository itemRepo;
    private final PpeAssignmentRepository assignmentRepo;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public PpeService(PpeItemRepository itemRepo,
                      PpeAssignmentRepository assignmentRepo,
                      AuditService audit,
                      CurrentRequest currentRequest) {
        this.itemRepo = itemRepo;
        this.assignmentRepo = assignmentRepo;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional
    public PpeAssignment issue(UUID employeeId,
                                UUID ppeItemId,
                                LocalDate issuedAt,
                                LocalDate expiryDate,
                                String conditionAtIssue,
                                String notes) {

        // If expiryDate not provided, compute from item's default_expiry_months
        LocalDate finalExpiryDate = expiryDate;
        if (finalExpiryDate == null) {
            PpeItem item = itemRepo.findByIdAndTenantId(ppeItemId, TenantContext.current())
                    .orElseThrow(() -> new ResourceNotFoundException("PPE item not found: " + ppeItemId));
            if (item.getDefaultExpiryMonths() != null) {
                finalExpiryDate = issuedAt.plusMonths(item.getDefaultExpiryMonths());
            } else {
                // Fallback to 12 months if no default
                finalExpiryDate = issuedAt.plusMonths(12);
            }
        }

        PpeAssignment assignment = new PpeAssignment();
        assignment.setTenantId(TenantContext.current());
        assignment.setEmployeeId(employeeId);
        assignment.setPpeItemId(ppeItemId);
        assignment.setIssuedAt(issuedAt);
        assignment.setExpiryDate(finalExpiryDate);
        assignment.setConditionAtIssue(conditionAtIssue);
        assignment.setNotes(notes);
        assignment.setCreatedBy(currentRequest.username());
        assignment.setUpdatedBy(currentRequest.username());

        PpeAssignment saved = assignmentRepo.save(assignment);

        audit.record(MODULE, ENTITY_ASSIGNMENT, saved.getId().toString(), "ISSUED", null, null);

        return saved;
    }

    @Transactional
    public PpeAssignment returnPpe(UUID assignmentId,
                                    LocalDate returnedAt,
                                    String conditionAtReturn) {

        PpeAssignment assignment = getAssignment(assignmentId);

        assignment.setReturnedAt(returnedAt);
        assignment.setConditionAtReturn(conditionAtReturn);
        assignment.setUpdatedBy(currentRequest.username());
        assignment.setUpdatedAt(OffsetDateTime.now());

        PpeAssignment updated = assignmentRepo.save(assignment);

        audit.record(MODULE, ENTITY_ASSIGNMENT, assignmentId.toString(), "RETURNED", null, null);

        return updated;
    }

    @Transactional(readOnly = true)
    public PpeAssignment getAssignment(UUID id) {
        PpeAssignment assignment = assignmentRepo.findByIdAndTenantId(id, TenantContext.current())
                .orElseThrow(() -> new ResourceNotFoundException("PPE assignment not found: " + id));

        // Tenant post-check
        if (!TenantContext.current().equals(assignment.getTenantId())) {
            throw new ResourceNotFoundException("PPE assignment not found: " + id);
        }

        return assignment;
    }

    @Transactional(readOnly = true)
    public List<PpeAssignment> listAssignments(UUID employeeId) {
        if (employeeId != null) {
            return assignmentRepo.findByTenantIdAndEmployeeIdOrderByIssuedAtDesc(TenantContext.current(), employeeId);
        } else {
            return assignmentRepo.findByTenantIdOrderByIssuedAtDesc(TenantContext.current());
        }
    }

    /**
     * Expiring PPE (within 30 days from today, not yet returned).
     * Exposed for potential ExpiryAlertScheduler integration (not wired in this milestone).
     */
    @Transactional(readOnly = true)
    public List<PpeAssignment> findExpiringAssignments() {
        LocalDate today = LocalDate.now();
        LocalDate thirtyDaysFromNow = today.plusDays(30);
        return assignmentRepo.findExpiringAssignments(TenantContext.current(), today, thirtyDaysFromNow);
    }

    // ── PPE Item Management ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PpeItem> listItems(Boolean activeOnly) {
        if (activeOnly != null && activeOnly) {
            return itemRepo.findByTenantIdAndActiveOrderByName(TenantContext.current(), true);
        } else {
            return itemRepo.findByTenantIdOrderByName(TenantContext.current());
        }
    }

    @Transactional(readOnly = true)
    public PpeItem getItem(UUID id) {
        return itemRepo.findByIdAndTenantId(id, TenantContext.current())
                .orElseThrow(() -> new ResourceNotFoundException("PPE item not found: " + id));
    }
}
