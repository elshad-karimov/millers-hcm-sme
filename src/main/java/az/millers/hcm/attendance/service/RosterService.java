package az.millers.hcm.attendance.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.attendance.api.dto.RosterDtos.AssignRequest;
import az.millers.hcm.attendance.api.dto.RosterDtos.BulkAssignRequest;
import az.millers.hcm.attendance.api.dto.RosterDtos.RosterEntryResponse;
import az.millers.hcm.attendance.api.dto.RosterDtos.RosterGrid;
import az.millers.hcm.attendance.api.dto.RosterDtos.SwapRequest;
import az.millers.hcm.attendance.domain.RosterEntry;
import az.millers.hcm.attendance.domain.Shift;
import az.millers.hcm.attendance.repo.RosterEntryRepository;
import az.millers.hcm.attendance.repo.ShiftRepository;
import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * Roster CRUD + bulk assign + swap (M110).
 *
 * <p>Invariants:
 * <ul>
 *   <li>At most one shift per (employee, date) — DB partial unique index +
 *       service-level upsert behaviour for re-assignment.</li>
 *   <li>Locked rows are immutable — they go through the swap flow, never a
 *       silent overwrite.</li>
 *   <li>Bulk-assign is all-or-nothing: any single failure rolls back the
 *       transaction so partial publishes can't happen.</li>
 * </ul>
 */
@Service
public class RosterService {

    private static final String MODULE = "ATTENDANCE";
    private static final String ENTITY = "RosterEntry";

    private final RosterEntryRepository roster;
    private final ShiftRepository shifts;
    private final EmployeeRepository employees;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public RosterService(RosterEntryRepository roster,
                         ShiftRepository shifts,
                         EmployeeRepository employees,
                         AuditService audit,
                         CurrentRequest currentRequest) {
        this.roster = roster;
        this.shifts = shifts;
        this.employees = employees;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    // ─── Queries ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public RosterEntry get(UUID id) {
        return roster.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Roster entry not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<RosterEntryResponse> forEmployee(UUID employeeId, LocalDate from, LocalDate to) {
        validateRange(from, to);
        List<RosterEntry> rows = roster.findByEmployeeIdAndRosterDateBetweenOrderByRosterDateAsc(
                employeeId, from, to);
        return decorate(rows);
    }

    @Transactional(readOnly = true)
    public RosterGrid grid(List<UUID> employeeIds, LocalDate from, LocalDate to) {
        validateRange(from, to);
        if (employeeIds == null || employeeIds.isEmpty()) {
            return new RosterGrid(from, to, List.of());
        }
        List<RosterEntry> rows = roster.findForEmployeesBetween(employeeIds, from, to);

        // Decorate in one pass.
        Map<UUID, Shift> shiftCache = new HashMap<>();
        Map<UUID, Employee> empCache = new HashMap<>();
        Map<UUID, List<RosterEntryResponse>> byEmployee = new HashMap<>();
        for (UUID eid : employeeIds) {
            byEmployee.put(eid, new ArrayList<>());
        }
        for (RosterEntry r : rows) {
            Shift s = shiftCache.computeIfAbsent(r.getShiftId(),
                    id -> shifts.findById(id).orElse(null));
            Employee e = empCache.computeIfAbsent(r.getEmployeeId(),
                    id -> employees.findById(id).orElse(null));
            String name = e == null ? null : (e.getFirstName() + " " + e.getLastName());
            byEmployee.get(r.getEmployeeId()).add(RosterEntryResponse.from(r, s, name));
        }

        List<RosterGrid.EmployeeRow> result = new ArrayList<>(employeeIds.size());
        for (UUID eid : employeeIds) {
            Employee e = empCache.computeIfAbsent(eid,
                    id -> employees.findById(id).orElse(null));
            String name = e == null ? null : (e.getFirstName() + " " + e.getLastName());
            result.add(new RosterGrid.EmployeeRow(eid, name, byEmployee.get(eid)));
        }
        return new RosterGrid(from, to, result);
    }

    // ─── Mutations ───────────────────────────────────────────────────────

    @Transactional
    public RosterEntry assign(AssignRequest req) {
        Shift s = ensureShiftAssignable(req.shiftId());
        // M271 — load the full Employee so we can gate on employment status.
        // Previously only checked existsById, which let HR roster a terminated
        // / retired / suspended / garden-leave employee. After M269 the
        // ex-employee's SSO is disabled and grants are revoked, but their
        // ID lingered in the roster pool.
        var emp = employees.findById(req.employeeId())
                .orElseThrow(() -> new BadRequestException(
                        "Employee not found: " + req.employeeId()));
        var status = emp.getEmploymentStatus();
        if (status != null && !status.isAvailableForRostering()) {
            throw new BadRequestException(
                    "Employee " + emp.getEmployeeNo() + " is " + status
                    + " — not available for rostering");
        }
        Optional<RosterEntry> existingOpt =
                roster.findByEmployeeIdAndRosterDate(req.employeeId(), req.rosterDate());
        if (existingOpt.isPresent() && existingOpt.get().isLocked()) {
            throw new BadRequestException(
                    "Roster entry for " + req.rosterDate() + " is locked — use the swap flow");
        }
        RosterEntry r = existingOpt.orElseGet(RosterEntry::new);
        String action = existingOpt.isPresent() ? "REASSIGN" : "ASSIGN";
        RosterEntryResponse before = existingOpt
                .map(e -> RosterEntryResponse.from(e, s, null)).orElse(null);
        r.setEmployeeId(req.employeeId());
        r.setShiftId(s.getId());
        r.setRosterDate(req.rosterDate());
        r.setNotes(req.notes());
        if (r.getCreatedBy() == null) r.setCreatedBy(currentRequest.username());
        r.setUpdatedBy(currentRequest.username());
        RosterEntry saved = roster.save(r);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                action, before, RosterEntryResponse.from(saved, s, null));
        return saved;
    }

    @Transactional
    public List<RosterEntry> bulkAssign(BulkAssignRequest req) {
        if (req.entries() == null || req.entries().isEmpty()) {
            throw new BadRequestException("Bulk assign requires at least one entry");
        }
        // Reject duplicate (employee, date) pairs in the same batch — a no-op
        // that would otherwise silently let the last one win.
        Set<String> seen = new HashSet<>();
        for (AssignRequest a : req.entries()) {
            if (a == null) {
                throw new BadRequestException("Null entry in batch");
            }
            String key = a.employeeId() + "@" + a.rosterDate();
            if (!seen.add(key)) {
                throw new BadRequestException(
                        "Duplicate (employee, date) in batch: " + key);
            }
        }
        List<RosterEntry> saved = new ArrayList<>(req.entries().size());
        for (AssignRequest a : req.entries()) {
            saved.add(assign(a));
        }
        return saved;
    }

    @Transactional
    public RosterEntry remove(UUID id) {
        RosterEntry r = get(id);
        if (r.isLocked()) {
            throw new BadRequestException("Roster entry is locked — use the swap flow");
        }
        RosterEntryResponse before = RosterEntryResponse.from(
                r, shifts.findById(r.getShiftId()).orElse(null), null);
        roster.delete(r);
        audit.record(MODULE, ENTITY, id.toString(), "REMOVE", before, null);
        return r;
    }

    @Transactional
    public RosterEntry lock(UUID id) {
        RosterEntry r = get(id);
        if (r.isLocked()) {
            throw new BadRequestException("Roster entry is already locked");
        }
        RosterEntryResponse before = RosterEntryResponse.from(
                r, shifts.findById(r.getShiftId()).orElse(null), null);
        r.setLocked(true);
        r.setUpdatedBy(currentRequest.username());
        RosterEntry saved = roster.save(r);
        audit.record(MODULE, ENTITY, id.toString(), "LOCK", before,
                RosterEntryResponse.from(saved, null, null));
        return saved;
    }

    /**
     * Swap two roster entries — A's shift goes to B's slot and vice-versa.
     * Works on locked entries (that's the whole point of the swap flow). The
     * dates aren't touched; only {@code shiftId} flips between the two rows.
     */
    @Transactional
    public List<RosterEntry> swap(SwapRequest req) {
        if (req.entryAId().equals(req.entryBId())) {
            throw new BadRequestException("Cannot swap an entry with itself");
        }
        RosterEntry a = get(req.entryAId());
        RosterEntry b = get(req.entryBId());
        UUID newAShift = b.getShiftId();
        UUID newBShift = a.getShiftId();

        RosterEntryResponse beforeA = RosterEntryResponse.from(
                a, shifts.findById(a.getShiftId()).orElse(null), null);
        RosterEntryResponse beforeB = RosterEntryResponse.from(
                b, shifts.findById(b.getShiftId()).orElse(null), null);

        a.setShiftId(newAShift);
        a.setUpdatedBy(currentRequest.username());
        b.setShiftId(newBShift);
        b.setUpdatedBy(currentRequest.username());
        RosterEntry savedA = roster.save(a);
        RosterEntry savedB = roster.save(b);

        Map<String, Object> swapMeta = Map.of(
                "entryA", a.getId().toString(),
                "entryB", b.getId().toString(),
                "reason", req.reason() == null ? "" : req.reason());
        audit.record(MODULE, ENTITY, a.getId().toString(),
                "SWAP", beforeA,
                Map.of("newShiftId", newAShift.toString(), "swap", swapMeta));
        audit.record(MODULE, ENTITY, b.getId().toString(),
                "SWAP", beforeB,
                Map.of("newShiftId", newBShift.toString(), "swap", swapMeta));
        return List.of(savedA, savedB);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private Shift ensureShiftAssignable(UUID shiftId) {
        Shift s = shifts.findById(shiftId)
                .orElseThrow(() -> new BadRequestException("Shift not found: " + shiftId));
        if (!s.isActive()) {
            throw new BadRequestException("Shift " + s.getCode() + " is inactive");
        }
        return s;
    }

    private List<RosterEntryResponse> decorate(List<RosterEntry> rows) {
        if (rows.isEmpty()) return List.of();
        Map<UUID, Shift> shiftCache = new HashMap<>();
        Map<UUID, Employee> empCache = new HashMap<>();
        List<RosterEntryResponse> out = new ArrayList<>(rows.size());
        for (RosterEntry r : rows) {
            Shift s = shiftCache.computeIfAbsent(r.getShiftId(),
                    id -> shifts.findById(id).orElse(null));
            Employee e = empCache.computeIfAbsent(r.getEmployeeId(),
                    id -> employees.findById(id).orElse(null));
            String name = e == null ? null : (e.getFirstName() + " " + e.getLastName());
            out.add(RosterEntryResponse.from(r, s, name));
        }
        return out;
    }

    /** Package-private — pinned by the unit test. */
    static void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BadRequestException("from and to are required");
        }
        if (to.isBefore(from)) {
            throw new BadRequestException("'to' must be on or after 'from'");
        }
        if (from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
            throw new BadRequestException(
                    "Date range too wide — max " + MAX_RANGE_DAYS + " days");
        }
    }

    /** 92 days = roughly a quarter. Reasonable upper bound for a single grid load. */
    static final int MAX_RANGE_DAYS = 92;
}
