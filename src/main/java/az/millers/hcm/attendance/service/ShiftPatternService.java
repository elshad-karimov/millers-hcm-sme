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
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.attendance.api.dto.ShiftPatternDtos.AssignmentRequest;
import az.millers.hcm.attendance.api.dto.ShiftPatternDtos.AssignmentResponse;
import az.millers.hcm.attendance.api.dto.ShiftPatternDtos.EndAssignmentRequest;
import az.millers.hcm.attendance.api.dto.ShiftPatternDtos.GenerateRosterRequest;
import az.millers.hcm.attendance.api.dto.ShiftPatternDtos.GenerateRosterResponse;
import az.millers.hcm.attendance.api.dto.ShiftPatternDtos.PatternDayRequest;
import az.millers.hcm.attendance.api.dto.ShiftPatternDtos.PatternDayResponse;
import az.millers.hcm.attendance.api.dto.ShiftPatternDtos.PatternRequest;
import az.millers.hcm.attendance.api.dto.ShiftPatternDtos.PatternResponse;
import az.millers.hcm.attendance.domain.PatternAssignment;
import az.millers.hcm.attendance.domain.RosterEntry;
import az.millers.hcm.attendance.domain.Shift;
import az.millers.hcm.attendance.domain.ShiftPattern;
import az.millers.hcm.attendance.domain.ShiftPatternDay;
import az.millers.hcm.attendance.repo.PatternAssignmentRepository;
import az.millers.hcm.attendance.repo.RosterEntryRepository;
import az.millers.hcm.attendance.repo.ShiftPatternDayRepository;
import az.millers.hcm.attendance.repo.ShiftPatternRepository;
import az.millers.hcm.attendance.repo.ShiftRepository;
import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * Shift pattern CRUD + per-employee assignment + auto-roster generation
 * (M111).
 *
 * <p>The hard part is the cycle math, which lives in
 * {@link #cyclePositionFor(LocalDate, LocalDate, int, int)} — a pure-static
 * helper pinned by an extensive unit-test boundary set. Mis-implementing it
 * (off-by-one anchor, negative modulo on early dates, ignoring the anchor)
 * would silently roster the wrong shifts for entire teams.
 */
@Service
public class ShiftPatternService {

    private static final String MODULE = "ATTENDANCE";
    private static final String PATTERN_ENTITY = "ShiftPattern";
    private static final String ASSIGNMENT_ENTITY = "PatternAssignment";

    /** Hard ceiling on the date range for one generator call. */
    static final int MAX_GENERATE_DAYS = 366;

    private final ShiftPatternRepository patterns;
    private final ShiftPatternDayRepository patternDays;
    private final PatternAssignmentRepository assignments;
    private final ShiftRepository shifts;
    private final RosterEntryRepository roster;
    private final EmployeeRepository employees;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public ShiftPatternService(ShiftPatternRepository patterns,
                                ShiftPatternDayRepository patternDays,
                                PatternAssignmentRepository assignments,
                                ShiftRepository shifts,
                                RosterEntryRepository roster,
                                EmployeeRepository employees,
                                AuditService audit,
                                CurrentRequest currentRequest) {
        this.patterns = patterns;
        this.patternDays = patternDays;
        this.assignments = assignments;
        this.shifts = shifts;
        this.roster = roster;
        this.employees = employees;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    // ─── Patterns ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PatternResponse> listPatterns(boolean activeOnly) {
        List<ShiftPattern> rows = activeOnly
                ? patterns.findByActiveTrueOrderByNameAsc()
                : patterns.findAllByOrderByNameAsc();
        return rows.stream().map(this::decoratePattern).toList();
    }

    @Transactional(readOnly = true)
    public PatternResponse getPattern(UUID id) {
        ShiftPattern p = patterns.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pattern not found: " + id));
        return decoratePattern(p);
    }

    private PatternResponse decoratePattern(ShiftPattern p) {
        List<ShiftPatternDay> days = patternDays.findByPatternIdOrderByDayIndexAsc(p.getId());
        Map<UUID, Shift> shiftCache = new HashMap<>();
        List<PatternDayResponse> dayResponses = days.stream().map(d -> {
            Shift s = d.getShiftId() == null ? null
                    : shiftCache.computeIfAbsent(d.getShiftId(),
                            id -> shifts.findById(id).orElse(null));
            return PatternDayResponse.from(d, s);
        }).toList();
        return PatternResponse.from(p, dayResponses, assignments.countByPatternId(p.getId()));
    }

    @Transactional
    public PatternResponse createPattern(PatternRequest req) {
        validatePattern(req);
        if (patterns.existsByCode(req.code())) {
            throw new BadRequestException("Pattern code already exists: " + req.code());
        }
        ShiftPattern p = new ShiftPattern();
        applyPatternFields(p, req);
        p.setCreatedBy(currentRequest.username());
        ShiftPattern saved = patterns.save(p);
        replaceDays(saved.getId(), req.days());
        PatternResponse response = decoratePattern(saved);
        audit.record(MODULE, PATTERN_ENTITY, saved.getId().toString(),
                "CREATE", null, response);
        return response;
    }

    @Transactional
    public PatternResponse updatePattern(UUID id, PatternRequest req) {
        validatePattern(req);
        ShiftPattern p = patterns.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pattern not found: " + id));
        if (!p.getCode().equals(req.code()) && patterns.existsByCode(req.code())) {
            throw new BadRequestException("Pattern code already exists: " + req.code());
        }
        PatternResponse before = decoratePattern(p);
        applyPatternFields(p, req);
        patterns.save(p);
        replaceDays(id, req.days());
        PatternResponse response = decoratePattern(p);
        audit.record(MODULE, PATTERN_ENTITY, id.toString(),
                "UPDATE", before, response);
        return response;
    }

    private void applyPatternFields(ShiftPattern p, PatternRequest req) {
        p.setCode(req.code());
        p.setName(req.name());
        p.setDescription(req.description());
        p.setCycleDays(req.cycleDays());
        p.setActive(req.active() == null ? true : req.active());
    }

    private void replaceDays(UUID patternId, List<PatternDayRequest> days) {
        patternDays.deleteAllByPatternId(patternId);
        for (PatternDayRequest d : days) {
            if (d.shiftId() != null && !shifts.existsById(d.shiftId())) {
                throw new BadRequestException("Shift not found in pattern day "
                        + d.dayIndex() + ": " + d.shiftId());
            }
            ShiftPatternDay row = new ShiftPatternDay();
            row.setPatternId(patternId);
            row.setDayIndex(d.dayIndex());
            row.setShiftId(d.shiftId());
            row.setNotes(d.notes());
            patternDays.save(row);
        }
    }

    /** Package-private — pinned by the unit test. */
    static void validatePattern(PatternRequest req) {
        if (req.cycleDays() == null || req.cycleDays() < 1 || req.cycleDays() > 365) {
            throw new BadRequestException("cycleDays must be between 1 and 365");
        }
        if (req.days() == null || req.days().isEmpty()) {
            throw new BadRequestException("days is required");
        }
        if (req.days().size() != req.cycleDays()) {
            throw new BadRequestException("days must contain exactly " + req.cycleDays()
                    + " entries (one per cycle day); got " + req.days().size());
        }
        Set<Integer> seen = new HashSet<>();
        for (PatternDayRequest d : req.days()) {
            if (d.dayIndex() < 0 || d.dayIndex() >= req.cycleDays()) {
                throw new BadRequestException(
                        "dayIndex " + d.dayIndex() + " out of range [0, "
                        + (req.cycleDays() - 1) + "]");
            }
            if (!seen.add(d.dayIndex())) {
                throw new BadRequestException("Duplicate dayIndex: " + d.dayIndex());
            }
        }
    }

    // ─── Assignments ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AssignmentResponse> assignmentsForEmployee(UUID employeeId) {
        return decorateAssignments(assignments.findByEmployeeIdOrderByStartDateDesc(employeeId));
    }

    @Transactional(readOnly = true)
    public List<AssignmentResponse> assignmentsForPattern(UUID patternId) {
        return decorateAssignments(assignments.findByPatternIdOrderByStartDateDesc(patternId));
    }

    @Transactional
    public AssignmentResponse assign(AssignmentRequest req) {
        ShiftPattern p = patterns.findById(req.patternId())
                .orElseThrow(() -> new BadRequestException("Pattern not found: " + req.patternId()));
        if (!p.isActive()) {
            throw new BadRequestException("Pattern is inactive: " + p.getCode());
        }
        if (!employees.existsById(req.employeeId())) {
            throw new BadRequestException("Employee not found: " + req.employeeId());
        }
        if (req.endDate() != null && req.endDate().isBefore(req.startDate())) {
            throw new BadRequestException("endDate must be on or after startDate");
        }
        int anchor = req.anchorDayIndex() == null ? 0 : req.anchorDayIndex();
        if (anchor < 0 || anchor >= p.getCycleDays()) {
            throw new BadRequestException(
                    "anchorDayIndex (" + anchor + ") must be in [0, "
                    + (p.getCycleDays() - 1) + "]");
        }

        // Auto-close any existing open assignment so the partial unique index
        // doesn't trip when a manager reassigns a pattern.
        Optional<PatternAssignment> openOpt = assignments.findOpenForEmployee(req.employeeId());
        openOpt.ifPresent(prior -> {
            if (prior.getPatternId().equals(req.patternId())) {
                // Re-assigning the same pattern is a no-op-ish — close the prior
                // the day before the new start.
                if (req.startDate().isAfter(prior.getStartDate())) {
                    prior.setEndDate(req.startDate().minusDays(1));
                    assignments.save(prior);
                }
            } else {
                // Different pattern → close the old one.
                if (req.startDate().isAfter(prior.getStartDate())) {
                    prior.setEndDate(req.startDate().minusDays(1));
                } else {
                    // Same start day → drop the prior outright (overlap impossible).
                    prior.setEndDate(prior.getStartDate());
                }
                assignments.save(prior);
            }
        });

        PatternAssignment a = new PatternAssignment();
        a.setEmployeeId(req.employeeId());
        a.setPatternId(req.patternId());
        a.setStartDate(req.startDate());
        a.setEndDate(req.endDate());
        a.setAnchorDayIndex(anchor);
        a.setNotes(req.notes());
        a.setCreatedBy(currentRequest.username());
        a.setUpdatedBy(currentRequest.username());
        PatternAssignment saved = assignments.save(a);
        AssignmentResponse response = AssignmentResponse.from(saved, p, employeeName(req.employeeId()));
        audit.record(MODULE, ASSIGNMENT_ENTITY, saved.getId().toString(),
                "ASSIGN", null, response);
        return response;
    }

    @Transactional
    public AssignmentResponse endAssignment(UUID id, EndAssignmentRequest req) {
        PatternAssignment a = assignments.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found: " + id));
        if (a.getEndDate() != null) {
            throw new BadRequestException("Assignment already ended on " + a.getEndDate());
        }
        if (req.endDate() == null || req.endDate().isBefore(a.getStartDate())) {
            throw new BadRequestException("endDate must be on or after startDate");
        }
        ShiftPattern p = patterns.findById(a.getPatternId()).orElse(null);
        AssignmentResponse before = AssignmentResponse.from(a, p, employeeName(a.getEmployeeId()));
        a.setEndDate(req.endDate());
        a.setNotes((a.getNotes() == null ? "" : a.getNotes() + "\n")
                + "Ended: " + (req.reason() == null ? "" : req.reason()));
        a.setUpdatedBy(currentRequest.username());
        PatternAssignment saved = assignments.save(a);
        AssignmentResponse response = AssignmentResponse.from(saved, p, employeeName(a.getEmployeeId()));
        audit.record(MODULE, ASSIGNMENT_ENTITY, id.toString(),
                "END", before, response);
        return response;
    }

    // ─── Auto-roster generation ─────────────────────────────────────────

    @Transactional
    public GenerateRosterResponse generateRoster(GenerateRosterRequest req) {
        if (req.from() == null || req.to() == null) {
            throw new BadRequestException("from and to are required");
        }
        if (req.to().isBefore(req.from())) {
            throw new BadRequestException("'to' must be on or after 'from'");
        }
        if (req.from().plusDays(MAX_GENERATE_DAYS).isBefore(req.to())) {
            throw new BadRequestException(
                    "Generation range too wide — max " + MAX_GENERATE_DAYS + " days");
        }
        boolean overwrite = req.overwriteExisting() != null && req.overwriteExisting();

        List<PatternAssignment> overlapping = assignments.findOverlapping(
                req.from(), req.to(),
                req.employeeIds() == null || req.employeeIds().isEmpty() ? null : req.employeeIds());
        if (overlapping.isEmpty()) {
            return new GenerateRosterResponse(0, 0, 0, 0, 0);
        }

        // Pre-load pattern + day maps.
        Set<UUID> patternIds = overlapping.stream()
                .map(PatternAssignment::getPatternId).collect(Collectors.toSet());
        Map<UUID, ShiftPattern> patternMap = patterns.findAllById(patternIds).stream()
                .collect(Collectors.toMap(ShiftPattern::getId, p -> p));
        Map<UUID, Map<Integer, ShiftPatternDay>> daysByPattern = new HashMap<>();
        for (UUID pid : patternIds) {
            Map<Integer, ShiftPatternDay> map = new HashMap<>();
            for (ShiftPatternDay d : patternDays.findByPatternIdOrderByDayIndexAsc(pid)) {
                map.put(d.getDayIndex(), d);
            }
            daysByPattern.put(pid, map);
        }

        // Group assignments by employee so we can fold them into the date range
        // (an employee could have several historic assignments in the same window).
        Map<UUID, List<PatternAssignment>> byEmployee = new HashMap<>();
        for (PatternAssignment a : overlapping) {
            byEmployee.computeIfAbsent(a.getEmployeeId(), k -> new ArrayList<>()).add(a);
        }

        int rowsCreated = 0;
        int rowsUpdated = 0;
        int rowsSkippedLocked = 0;
        int restDaysSkipped = 0;

        for (Map.Entry<UUID, List<PatternAssignment>> entry : byEmployee.entrySet()) {
            UUID empId = entry.getKey();
            List<PatternAssignment> list = entry.getValue();
            for (LocalDate date = req.from(); !date.isAfter(req.to()); date = date.plusDays(1)) {
                PatternAssignment effective = pickAssignmentFor(list, date);
                if (effective == null) continue;
                ShiftPattern pat = patternMap.get(effective.getPatternId());
                if (pat == null) continue;
                int dayIndex = cyclePositionFor(effective.getStartDate(), date,
                        effective.getAnchorDayIndex(), pat.getCycleDays());
                ShiftPatternDay day = daysByPattern.get(pat.getId()).get(dayIndex);
                if (day == null || day.getShiftId() == null) {
                    restDaysSkipped++;
                    continue;
                }

                Optional<RosterEntry> existing = roster
                        .findByEmployeeIdAndRosterDate(empId, date);
                if (existing.isPresent()) {
                    RosterEntry r = existing.get();
                    if (r.isLocked()) { rowsSkippedLocked++; continue; }
                    if (!overwrite) continue;
                    if (r.getShiftId().equals(day.getShiftId())) continue;
                    r.setShiftId(day.getShiftId());
                    r.setUpdatedBy(currentRequest.username());
                    roster.save(r);
                    rowsUpdated++;
                } else {
                    RosterEntry r = new RosterEntry();
                    r.setEmployeeId(empId);
                    r.setShiftId(day.getShiftId());
                    r.setRosterDate(date);
                    r.setCreatedBy(currentRequest.username());
                    r.setUpdatedBy(currentRequest.username());
                    roster.save(r);
                    rowsCreated++;
                }
            }
        }

        GenerateRosterResponse result = new GenerateRosterResponse(
                byEmployee.size(), rowsCreated, rowsUpdated,
                rowsSkippedLocked, restDaysSkipped);
        audit.record(MODULE, ASSIGNMENT_ENTITY, "GENERATE",
                "GENERATE_ROSTER", req, result);
        return result;
    }

    /** Picks the assignment in {@code list} that covers {@code date}, or null. */
    static PatternAssignment pickAssignmentFor(List<PatternAssignment> list, LocalDate date) {
        for (PatternAssignment a : list) {
            if (date.isBefore(a.getStartDate())) continue;
            if (a.getEndDate() != null && date.isAfter(a.getEndDate())) continue;
            return a;
        }
        return null;
    }

    // ─── Cycle math (the heart of M111) ──────────────────────────────────

    /**
     * Maps a calendar date to its position inside a rotation cycle.
     *
     * <p>{@code dayIndex = (anchor + daysSinceStart) mod cycleDays}.
     *
     * <p>Examples (cycle=7, anchor=0, start=2026-01-01 which is a Thursday):
     * <ul>
     *   <li>2026-01-01 → 0</li>
     *   <li>2026-01-07 → 6</li>
     *   <li>2026-01-08 → 0 (cycle wraps)</li>
     * </ul>
     *
     * <p>{@code anchor=3} shifts the employee three positions into the cycle
     * relative to the assignment start. Useful for staggering team rotations
     * so not everyone is on the same shift on the same day.
     *
     * @throws IllegalArgumentException if {@code cycleDays < 1}, {@code date}
     *         is before {@code start}, or {@code anchor} is negative.
     */
    public static int cyclePositionFor(LocalDate start, LocalDate date,
                                        int anchor, int cycleDays) {
        if (start == null || date == null) {
            throw new IllegalArgumentException("start and date are required");
        }
        if (cycleDays < 1) {
            throw new IllegalArgumentException("cycleDays must be >= 1");
        }
        if (anchor < 0) {
            throw new IllegalArgumentException("anchor must be >= 0");
        }
        if (date.isBefore(start)) {
            throw new IllegalArgumentException(
                    "date (" + date + ") is before assignment start (" + start + ")");
        }
        long delta = java.time.temporal.ChronoUnit.DAYS.between(start, date);
        // (anchor + delta) % cycleDays — both inputs non-negative, no negative-modulo concern.
        long pos = (anchor + delta) % cycleDays;
        return (int) pos;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private String employeeName(UUID employeeId) {
        return employees.findById(employeeId)
                .map(Employee.class::cast)
                .map(e -> e.getFirstName() + " " + e.getLastName())
                .orElse(null);
    }

    private List<AssignmentResponse> decorateAssignments(List<PatternAssignment> rows) {
        if (rows.isEmpty()) return List.of();
        Map<UUID, ShiftPattern> pCache = new HashMap<>();
        Map<UUID, String> nameCache = new HashMap<>();
        List<AssignmentResponse> out = new ArrayList<>(rows.size());
        for (PatternAssignment a : rows) {
            ShiftPattern p = pCache.computeIfAbsent(a.getPatternId(),
                    id -> patterns.findById(id).orElse(null));
            String name = nameCache.computeIfAbsent(a.getEmployeeId(), this::employeeName);
            out.add(AssignmentResponse.from(a, p, name));
        }
        return out;
    }
}
