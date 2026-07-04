package az.millers.hcm.attendance.service;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.attendance.api.dto.ShiftDtos.ShiftRequest;
import az.millers.hcm.attendance.api.dto.ShiftDtos.ShiftResponse;
import az.millers.hcm.attendance.domain.Shift;
import az.millers.hcm.attendance.repo.RosterEntryRepository;
import az.millers.hcm.attendance.repo.ShiftRepository;
import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.security.CurrentRequest;

/**
 * Shift catalog CRUD (M110). Cross-midnight detection and span/duration math
 * live in pure-static helpers so the unit suite can pin them without Spring.
 */
@Service
public class ShiftService {

    private static final String MODULE = "ATTENDANCE";
    private static final String ENTITY = "Shift";

    private final ShiftRepository shifts;
    private final RosterEntryRepository roster;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public ShiftService(ShiftRepository shifts,
                        RosterEntryRepository roster,
                        AuditService audit,
                        CurrentRequest currentRequest) {
        this.shifts = shifts;
        this.roster = roster;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    @Transactional(readOnly = true)
    public List<Shift> list(boolean activeOnly) {
        return activeOnly ? shifts.findByActiveTrueOrderByNameAsc()
                          : shifts.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Shift get(UUID id) {
        return shifts.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found: " + id));
    }

    @Transactional
    public Shift create(ShiftRequest req) {
        validate(req);
        if (shifts.existsByCode(req.code())) {
            throw new BadRequestException("Shift code already exists: " + req.code());
        }
        Shift s = new Shift();
        apply(s, req);
        s.setCreatedBy(currentRequest.username());
        Shift saved = shifts.save(s);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, ShiftResponse.from(saved));
        return saved;
    }

    @Transactional
    public Shift update(UUID id, ShiftRequest req) {
        validate(req);
        Shift s = get(id);
        if (!s.getCode().equals(req.code()) && shifts.existsByCode(req.code())) {
            throw new BadRequestException("Shift code already exists: " + req.code());
        }
        ShiftResponse before = ShiftResponse.from(s);
        apply(s, req);
        Shift saved = shifts.save(s);
        audit.record(MODULE, ENTITY, id.toString(),
                "UPDATE", before, ShiftResponse.from(saved));
        return saved;
    }

    @Transactional
    public Shift archive(UUID id) {
        Shift s = get(id);
        if (!s.isActive()) {
            throw new BadRequestException("Shift is already inactive");
        }
        if (roster.countByShiftId(id) > 0) {
            // Archiving (not deleting) is fine — historical roster rows keep
            // their pointer. The flag just hides the shift from new
            // assignments going forward.
        }
        ShiftResponse before = ShiftResponse.from(s);
        s.setActive(false);
        Shift saved = shifts.save(s);
        audit.record(MODULE, ENTITY, id.toString(),
                "ARCHIVE", before, ShiftResponse.from(saved));
        return saved;
    }

    private void apply(Shift s, ShiftRequest req) {
        s.setCode(req.code());
        s.setName(req.name());
        s.setDescription(req.description());
        s.setStartTime(req.startTime());
        s.setEndTime(req.endTime());
        s.setBreakMinutes(req.breakMinutes() == null ? 0 : req.breakMinutes());
        s.setCrossesMidnight(crossesMidnight(req.startTime(), req.endTime()));
        s.setColor(req.color());
        s.setActive(req.active() == null ? true : req.active());
    }

    /** Package-private for direct testing. */
    static void validate(ShiftRequest req) {
        if (req.startTime() == null || req.endTime() == null) {
            throw new BadRequestException("startTime and endTime are required");
        }
        if (req.startTime().equals(req.endTime())) {
            throw new BadRequestException("startTime and endTime cannot be identical");
        }
        int breakMin = req.breakMinutes() == null ? 0 : req.breakMinutes();
        if (breakMin < 0) {
            throw new BadRequestException("breakMinutes must be non-negative");
        }
        int span = spanMinutes(req.startTime(), req.endTime(),
                crossesMidnight(req.startTime(), req.endTime()));
        if (breakMin >= span) {
            throw new BadRequestException(
                    "breakMinutes (" + breakMin + ") must be less than shift span ("
                    + span + " minutes)");
        }
        if (req.color() != null && !req.color().isBlank()
                && !req.color().matches("^#[0-9A-Fa-f]{6}$")) {
            throw new BadRequestException("color must be a #RRGGBB hex string");
        }
    }

    // ─── Pure math (package-static; pinned by the unit test) ─────────────

    /** Auto-detects cross-midnight: end &lt; start (e.g., 22:00 → 06:00). */
    public static boolean crossesMidnight(LocalTime start, LocalTime end) {
        if (start == null || end == null) return false;
        return end.isBefore(start);
    }

    /**
     * Minute span between start and end, accounting for cross-midnight. Does
     * NOT subtract the break. Pure math, no entity dependency.
     */
    public static int spanMinutes(LocalTime start, LocalTime end, boolean crossesMidnight) {
        if (start == null || end == null) return 0;
        int startM = start.getHour() * 60 + start.getMinute();
        int endM = end.getHour() * 60 + end.getMinute();
        if (crossesMidnight) {
            return (24 * 60 - startM) + endM;
        }
        return Math.max(0, endM - startM);
    }
}
