package az.millers.hcm.staffing.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.staffing.domain.Position;
import az.millers.hcm.staffing.domain.PositionBudget;
import az.millers.hcm.staffing.repo.PositionBudgetRepository;
import az.millers.hcm.staffing.repo.PositionRepository;

/**
 * M244 — versioned position budget (PRD §6).
 *
 * <p>Owns all CRUD for {@link PositionBudget}. Service is the only place
 * that validates "no overlapping windows", auto-closes the previous
 * open-ended budget when a new one starts, and writes audit rows.
 * Per the standing "develop once, use everywhere" rule.
 */
@Service
public class PositionBudgetService {

    private static final String MODULE = "STAFFING";
    private static final String ENTITY = "PositionBudget";

    private final PositionBudgetRepository budgets;
    private final PositionRepository positions;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public PositionBudgetService(PositionBudgetRepository budgets,
                                  PositionRepository positions,
                                  AuditService audit,
                                  CurrentRequest currentRequest) {
        this.budgets = budgets;
        this.positions = positions;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<PositionBudget> listForPosition(UUID positionId) {
        assertPositionExists(positionId);
        return budgets.findByPositionIdOrderByEffectiveFromDesc(positionId);
    }

    @Transactional(readOnly = true)
    public Optional<PositionBudget> currentBudget(UUID positionId) {
        return budgets.currentBudget(positionId, LocalDate.now());
    }

    @Transactional
    public PositionBudget create(UUID positionId, PositionBudget input) {
        Position p = assertPositionExists(positionId);
        if (input.getEffectiveFrom() == null) {
            throw new BadRequestException("effectiveFrom is required");
        }
        if (input.getEffectiveTo() != null && input.getEffectiveTo().isBefore(input.getEffectiveFrom())) {
            throw new BadRequestException("effectiveTo cannot be before effectiveFrom");
        }
        assertNoOverlap(positionId, input.getEffectiveFrom(), input.getEffectiveTo(), null);

        // If the previous open-ended budget should auto-close at the new
        // effectiveFrom-1, do it here to keep windows tidy without forcing
        // the caller to manage both rows.
        budgets.findByPositionIdOrderByEffectiveFromDesc(positionId).stream()
                .filter(b -> b.getEffectiveTo() == null
                        && b.getEffectiveFrom().isBefore(input.getEffectiveFrom()))
                .findFirst()
                .ifPresent(prev -> {
                    prev.setEffectiveTo(input.getEffectiveFrom().minusDays(1));
                    prev.setUpdatedBy(currentRequest.username());
                    budgets.save(prev);
                });

        input.setId(null);
        input.setPositionId(positionId);
        if (input.getCurrency() == null || input.getCurrency().isBlank()) {
            input.setCurrency(p.getCurrency() == null ? "AZN" : p.getCurrency());
        }
        input.setCreatedBy(currentRequest.username());
        input.setUpdatedBy(currentRequest.username());
        PositionBudget saved = budgets.save(input);

        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, snapshot(saved));
        return saved;
    }

    @Transactional
    public PositionBudget update(UUID id, PositionBudget patch) {
        PositionBudget existing = budgets.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Budget not found: " + id));

        if (patch.getEffectiveFrom() != null) existing.setEffectiveFrom(patch.getEffectiveFrom());
        existing.setEffectiveTo(patch.getEffectiveTo()); // null is meaningful — open-ended
        if (patch.getBudgetedBasicSalary() != null) existing.setBudgetedBasicSalary(patch.getBudgetedBasicSalary());
        if (patch.getBudgetedAllowances()   != null) existing.setBudgetedAllowances(patch.getBudgetedAllowances());
        if (patch.getBudgetedEmployerTax()  != null) existing.setBudgetedEmployerTax(patch.getBudgetedEmployerTax());
        if (patch.getBudgetedBonus()        != null) existing.setBudgetedBonus(patch.getBudgetedBonus());
        if (patch.getBudgetedOvertime()     != null) existing.setBudgetedOvertime(patch.getBudgetedOvertime());
        if (patch.getBudgetedBenefits()     != null) existing.setBudgetedBenefits(patch.getBudgetedBenefits());
        if (patch.getCurrency()             != null) existing.setCurrency(patch.getCurrency());
        existing.setBudgetOwner(patch.getBudgetOwner());
        existing.setNotes(patch.getNotes());

        if (existing.getEffectiveTo() != null
                && existing.getEffectiveTo().isBefore(existing.getEffectiveFrom())) {
            throw new BadRequestException("effectiveTo cannot be before effectiveFrom");
        }
        assertNoOverlap(existing.getPositionId(),
                existing.getEffectiveFrom(), existing.getEffectiveTo(), existing.getId());

        existing.setUpdatedBy(currentRequest.username());
        PositionBudget saved = budgets.save(existing);

        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "UPDATE", null, snapshot(saved));
        return saved;
    }

    @Transactional
    public void delete(UUID id) {
        PositionBudget existing = budgets.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Budget not found: " + id));
        budgets.delete(existing);
        audit.record(MODULE, ENTITY, id.toString(),
                "DELETE", snapshot(existing), null);
    }

    private Position assertPositionExists(UUID positionId) {
        return positions.findById(positionId).orElseThrow(
                () -> new ResourceNotFoundException("Position not found: " + positionId));
    }

    /**
     * Reject inserts/updates that would overlap an existing window. Two
     * rows overlap when {@code a.from <= b.to} and {@code b.from <= a.to}
     * (treating null {@code to} as infinity).
     */
    private void assertNoOverlap(UUID positionId, LocalDate from, LocalDate to, UUID exceptId) {
        LocalDate hi = to == null ? LocalDate.of(9999, 12, 31) : to;
        var existing = budgets.findByPositionIdOrderByEffectiveFromDesc(positionId);
        for (PositionBudget b : existing) {
            if (exceptId != null && b.getId().equals(exceptId)) continue;
            LocalDate bHi = b.getEffectiveTo() == null ? LocalDate.of(9999, 12, 31) : b.getEffectiveTo();
            if (!from.isAfter(bHi) && !b.getEffectiveFrom().isAfter(hi)) {
                throw new BadRequestException(
                        "Budget window overlaps existing row " + b.getEffectiveFrom() + " → "
                                + (b.getEffectiveTo() == null ? "open" : b.getEffectiveTo()));
            }
        }
    }

    /** Compact audit snapshot — totals only, components in {@code BudgetSnapshot}. */
    public record BudgetSnapshot(
            UUID id, UUID positionId,
            LocalDate effectiveFrom, LocalDate effectiveTo,
            BigDecimal totalMonthly, BigDecimal totalAnnual,
            String currency) {}

    private BudgetSnapshot snapshot(PositionBudget b) {
        return new BudgetSnapshot(b.getId(), b.getPositionId(),
                b.getEffectiveFrom(), b.getEffectiveTo(),
                b.totalMonthly(), b.totalAnnual(), b.getCurrency());
    }
}
