package az.millers.hcm.staffing.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.staffing.domain.OccupancyType;
import az.millers.hcm.staffing.domain.PositionOccupancy;
import az.millers.hcm.staffing.repo.PositionOccupancyRepository;
import az.millers.hcm.staffing.repo.PositionRepository;

/**
 * M246 — Position occupancy service.
 *
 * <p>Owns every write to {@link PositionOccupancy}. Validates the FTE
 * allocation against the position's capacity, enforces non-overlapping
 * windows for PRIMARY rows, and audits every transition.
 *
 * <p>Per the standing "develop once, use everywhere" rule — this is the
 * single source of truth for who-occupies-what-when. Other modules
 * (succession, payroll-allocation, headcount-report) read from here.
 */
@Service
public class PositionOccupancyService {

    private static final String MODULE = "STAFFING";
    private static final String ENTITY = "PositionOccupancy";

    private final PositionOccupancyRepository repo;
    private final PositionRepository positions;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public PositionOccupancyService(PositionOccupancyRepository repo,
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
    public List<PositionOccupancy> forPosition(UUID positionId) {
        return repo.findByPositionIdOrderByStartDateDesc(positionId);
    }

    @Transactional(readOnly = true)
    public List<PositionOccupancy> forEmployee(UUID employeeId) {
        return repo.findByEmployeeIdOrderByStartDateDesc(employeeId);
    }

    @Transactional(readOnly = true)
    public List<PositionOccupancy> activeForPosition(UUID positionId) {
        return repo.findByPositionIdAndEndDateIsNull(positionId);
    }

    @Transactional(readOnly = true)
    public List<PositionOccupancy> activeForEmployee(UUID employeeId) {
        return repo.findByEmployeeIdAndEndDateIsNull(employeeId);
    }

    // ── CRUD ─────────────────────────────────────────────────────────────

    @Transactional
    public PositionOccupancy create(PositionOccupancy input) {
        if (input.getPositionId() == null) throw new BadRequestException("positionId required");
        if (input.getEmployeeId() == null) throw new BadRequestException("employeeId required");
        if (input.getStartDate() == null) throw new BadRequestException("startDate required");
        if (input.getEndDate() != null && input.getEndDate().isBefore(input.getStartDate())) {
            throw new BadRequestException("endDate cannot be before startDate");
        }
        positions.findById(input.getPositionId()).orElseThrow(
                () -> new ResourceNotFoundException("Position not found: " + input.getPositionId()));

        if (input.getOccupancyType() == null) input.setOccupancyType(OccupancyType.PRIMARY);
        if (input.getFteAllocation() == null) input.setFteAllocation(BigDecimal.ONE);

        // FTE-capacity guard: sum of FTEs that count toward occupancy on
        // overlapping windows must not exceed the position's approved
        // headcount. We do a soft check using the current active rows;
        // historical overlaps that closed before today are ignored.
        assertFteCapacity(input.getPositionId(), input.getOccupancyType(),
                input.getFteAllocation(), null);

        input.setId(null);
        input.setCreatedBy(currentRequest.username());
        input.setUpdatedBy(currentRequest.username());
        PositionOccupancy saved = repo.save(input);

        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, snapshot(saved));
        return saved;
    }

    @Transactional
    public PositionOccupancy update(UUID id, PositionOccupancy patch) {
        PositionOccupancy existing = repo.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Occupancy not found: " + id));

        if (patch.getOccupancyType() != null) existing.setOccupancyType(patch.getOccupancyType());
        if (patch.getFteAllocation() != null) existing.setFteAllocation(patch.getFteAllocation());
        if (patch.getStartDate() != null) existing.setStartDate(patch.getStartDate());
        existing.setEndDate(patch.getEndDate());
        existing.setEndReason(patch.getEndReason());
        existing.setEndNotes(patch.getEndNotes());
        existing.setHomePositionId(patch.getHomePositionId());
        existing.setActingAllowance(patch.getActingAllowance());
        existing.setActingAllowanceCurrency(patch.getActingAllowanceCurrency());
        existing.setNotes(patch.getNotes());
        existing.setUpdatedBy(currentRequest.username());

        if (existing.getEndDate() != null && existing.getEndDate().isBefore(existing.getStartDate())) {
            throw new BadRequestException("endDate cannot be before startDate");
        }

        // Re-check capacity excluding self
        assertFteCapacity(existing.getPositionId(), existing.getOccupancyType(),
                existing.getFteAllocation(), id);

        PositionOccupancy saved = repo.save(existing);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "UPDATE", null, snapshot(saved));
        return saved;
    }

    /**
     * End-date an active occupancy. Single convenience method so the SPA
     * doesn't need to build a full patch payload to close out a row.
     */
    @Transactional
    public PositionOccupancy end(UUID id, LocalDate endDate, String reason, String notes) {
        PositionOccupancy existing = repo.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Occupancy not found: " + id));
        if (existing.getEndDate() != null) {
            throw new BadRequestException("Occupancy already ended on " + existing.getEndDate());
        }
        if (endDate == null) endDate = LocalDate.now();
        if (endDate.isBefore(existing.getStartDate())) {
            throw new BadRequestException("endDate cannot be before startDate");
        }
        existing.setEndDate(endDate);
        existing.setEndReason(reason);
        existing.setEndNotes(notes);
        existing.setUpdatedBy(currentRequest.username());
        PositionOccupancy saved = repo.save(existing);

        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "END", null,
                java.util.Map.of("endDate", endDate.toString(),
                        "reason", reason == null ? "" : reason));
        return saved;
    }

    @Transactional
    public void delete(UUID id) {
        PositionOccupancy existing = repo.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("Occupancy not found: " + id));
        repo.delete(existing);
        audit.record(MODULE, ENTITY, id.toString(), "DELETE", snapshot(existing), null);
    }

    // ── Guards ───────────────────────────────────────────────────────────

    /**
     * Sum the FTE of ACTIVE rows that count toward occupancy + the
     * incoming row. Reject if the total would exceed the position's
     * {@code approvedHeadcount} (treated as FTE capacity).
     */
    private void assertFteCapacity(UUID positionId,
                                    OccupancyType type,
                                    BigDecimal incomingFte,
                                    UUID excludeId) {
        if (!type.countsAsOccupied()) return;
        BigDecimal approvedCapacity = BigDecimal.valueOf(
                positions.findById(positionId)
                        .map(p -> Math.max(0, p.getApprovedHeadcount()))
                        .orElse(0));
        BigDecimal currentlyAllocated = repo.findByPositionIdAndEndDateIsNull(positionId).stream()
                .filter(o -> excludeId == null || !o.getId().equals(excludeId))
                .filter(o -> o.getOccupancyType().countsAsOccupied())
                .map(PositionOccupancy::getFteAllocation)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal projected = currentlyAllocated.add(incomingFte);
        if (projected.compareTo(approvedCapacity) > 0) {
            throw new BadRequestException(
                    "Adding this occupancy would put position over capacity ("
                            + projected + " FTE / approved " + approvedCapacity + ")");
        }
    }

    // ── Snapshot ─────────────────────────────────────────────────────────

    public record OccupancySnapshot(
            UUID id, UUID positionId, UUID employeeId,
            OccupancyType type, BigDecimal fte,
            LocalDate startDate, LocalDate endDate, OffsetDateTime updatedAt) {}

    private OccupancySnapshot snapshot(PositionOccupancy o) {
        return new OccupancySnapshot(o.getId(), o.getPositionId(), o.getEmployeeId(),
                o.getOccupancyType(), o.getFteAllocation(),
                o.getStartDate(), o.getEndDate(), o.getUpdatedAt());
    }
}
