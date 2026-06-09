package az.millers.hcm.staffing.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.staffing.domain.PositionProfileItem;
import az.millers.hcm.staffing.domain.ProfileItemType;
import az.millers.hcm.staffing.repo.PositionProfileItemRepository;
import az.millers.hcm.staffing.repo.PositionRepository;

/**
 * M248 — Position profile service (PRD §25, §26, §27, §28, §29).
 *
 * <p>Owns the position-side definition of "what each position requires"
 * — allowances, required documents, mandatory training, equipment,
 * access roles, checklist items, approval limits.
 *
 * <p>Phase F delivers the definition + a {@link #grantPreviewFor}
 * helper that returns the list of grants that *would* be created if
 * the position were filled by an employee. Phase F.2 will wire the
 * actual cross-module auto-grant (allowance create, training enrol,
 * etc.).
 */
@Service
public class PositionProfileService {

    private static final String MODULE = "STAFFING";
    private static final String ENTITY = "PositionProfileItem";

    private final PositionProfileItemRepository repo;
    private final PositionRepository positions;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public PositionProfileService(PositionProfileItemRepository repo,
                                    PositionRepository positions,
                                    AuditService audit,
                                    CurrentRequest currentRequest) {
        this.repo = repo;
        this.positions = positions;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    // ── Reads ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PositionProfileItem> forPosition(UUID positionId) {
        return repo.findByPositionIdOrderByItemTypeAscSortOrderAscLabelAsc(positionId);
    }

    /**
     * Profile items grouped by type — used by the SPA panel to render
     * one section per category in a stable order.
     */
    @Transactional(readOnly = true)
    public Map<ProfileItemType, List<PositionProfileItem>> groupedForPosition(UUID positionId) {
        var items = forPosition(positionId);
        Map<ProfileItemType, List<PositionProfileItem>> by = new HashMap<>();
        for (var t : ProfileItemType.values()) by.put(t, new ArrayList<>());
        for (var it : items) by.get(it.getItemType()).add(it);
        return by;
    }

    // ── Grant preview ─────────────────────────────────────────────────────

    /**
     * Returns the list of grants that an HR officer would need to set up
     * for a new occupant of {@code positionId}. Filters to mandatory
     * items; the SPA can show optional items separately.
     *
     * <p>Phase F.2 will replace this preview with actual cross-module
     * calls (allowance.create, learning.enrol, etc.) on
     * {@code PositionOccupancyService.create}.
     */
    @Transactional(readOnly = true)
    public List<GrantPreview> grantPreviewFor(UUID positionId, UUID employeeId) {
        assertPositionExists(positionId);
        return forPosition(positionId).stream()
                .filter(PositionProfileItem::isMandatory)
                .map(it -> new GrantPreview(
                        it.getId(),
                        positionId,
                        employeeId,
                        it.getItemType(),
                        it.getLabel(),
                        it.getValueAmount(),
                        it.getCurrency(),
                        it.getReferenceCode(),
                        it.getNotes()))
                .collect(Collectors.toList());
    }

    public record GrantPreview(
            UUID profileItemId,
            UUID positionId,
            UUID employeeId,
            ProfileItemType itemType,
            String label,
            BigDecimal valueAmount,
            String currency,
            String referenceCode,
            String notes) {}

    // ── CRUD ──────────────────────────────────────────────────────────────

    @Transactional
    public PositionProfileItem create(UUID positionId, PositionProfileItem input) {
        assertPositionExists(positionId);
        if (input.getItemType() == null) throw new BadRequestException("itemType required");
        if (input.getLabel() == null || input.getLabel().isBlank())
            throw new BadRequestException("label required");

        input.setId(null);
        input.setPositionId(positionId);
        input.setCreatedBy(currentRequest.username());
        input.setUpdatedBy(currentRequest.username());
        PositionProfileItem saved = repo.save(input);

        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, snapshot(saved));
        return saved;
    }

    @Transactional
    public PositionProfileItem update(UUID id, PositionProfileItem patch) {
        PositionProfileItem existing = repo.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Profile item not found: " + id));
        if (patch.getItemType() != null) existing.setItemType(patch.getItemType());
        if (patch.getLabel() != null) existing.setLabel(patch.getLabel());
        existing.setValueAmount(patch.getValueAmount());
        existing.setCurrency(patch.getCurrency());
        existing.setMandatory(patch.isMandatory());
        existing.setReferenceCode(patch.getReferenceCode());
        existing.setNotes(patch.getNotes());
        existing.setSortOrder(patch.getSortOrder());
        existing.setUpdatedBy(currentRequest.username());

        PositionProfileItem saved = repo.save(existing);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "UPDATE", null, snapshot(saved));
        return saved;
    }

    @Transactional
    public void delete(UUID id) {
        PositionProfileItem existing = repo.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Profile item not found: " + id));
        repo.delete(existing);
        audit.record(MODULE, ENTITY, id.toString(), "DELETE", snapshot(existing), null);
    }

    /**
     * Clone the entire profile from a source position to a target position.
     * Useful when standing up a new position that mirrors an existing one.
     * Existing items on the target are NOT cleared — additive only.
     */
    @Transactional
    public List<PositionProfileItem> cloneFrom(UUID sourcePositionId, UUID targetPositionId) {
        assertPositionExists(sourcePositionId);
        assertPositionExists(targetPositionId);
        if (sourcePositionId.equals(targetPositionId)) {
            throw new BadRequestException("Source and target positions must differ");
        }
        var src = forPosition(sourcePositionId);
        List<PositionProfileItem> created = new ArrayList<>();
        for (var it : src) {
            PositionProfileItem copy = new PositionProfileItem();
            copy.setPositionId(targetPositionId);
            copy.setItemType(it.getItemType());
            copy.setLabel(it.getLabel());
            copy.setValueAmount(it.getValueAmount());
            copy.setCurrency(it.getCurrency());
            copy.setMandatory(it.isMandatory());
            copy.setReferenceCode(it.getReferenceCode());
            copy.setNotes(it.getNotes());
            copy.setSortOrder(it.getSortOrder());
            copy.setCreatedBy(currentRequest.username());
            copy.setUpdatedBy(currentRequest.username());
            created.add(repo.save(copy));
        }
        audit.record(MODULE, ENTITY, targetPositionId.toString(),
                "CLONE_FROM",
                Map.of("sourcePositionId", sourcePositionId.toString()),
                Map.of("clonedCount", created.size()));
        return created;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private void assertPositionExists(UUID positionId) {
        positions.findById(positionId).orElseThrow(
                () -> new ResourceNotFoundException("Position not found: " + positionId));
    }

    public record ItemSnapshot(UUID id, UUID positionId, ProfileItemType type,
                                String label, BigDecimal valueAmount, String currency,
                                boolean mandatory) {}

    private ItemSnapshot snapshot(PositionProfileItem it) {
        return new ItemSnapshot(it.getId(), it.getPositionId(), it.getItemType(),
                it.getLabel(), it.getValueAmount(), it.getCurrency(), it.isMandatory());
    }
}
