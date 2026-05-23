package az.millers.hcm.corehr.service;

import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.api.dto.HolidayRequest;
import az.millers.hcm.corehr.api.dto.HolidayResponse;
import az.millers.hcm.corehr.domain.Holiday;
import az.millers.hcm.corehr.repo.HolidayRepository;
import az.millers.hcm.security.CurrentRequest;

/**
 * Public-holiday calendar (PRD 8.4 / 8.5 / 8.9 — milestone 38).
 *
 * <p>Single source of truth for:
 * <ul>
 *   <li>{@link #isHoliday(LocalDate)} — used by
 *       {@code TimesheetGenerator} to render the {@code H} code on
 *       holidays that fall on what would otherwise be a working day.</li>
 *   <li>{@link #holidayDatesIn(YearMonth)} — used by
 *       {@code LeaveRequestService.computeDays} to subtract holiday
 *       dates when the leave type sets {@code exclude_holidays}.</li>
 *   <li>(future) the payroll OT engine for holiday-rate multipliers.</li>
 * </ul>
 *
 * <p>The default jurisdiction (e.g. {@code AZ}) is configurable via
 * {@code hcm.holiday.default-jurisdiction}. Callers can also pass an
 * explicit jurisdiction to the overloads when a multi-country
 * deployment needs to disambiguate.
 */
@Service
public class HolidayService {

    private static final String MODULE = "CORE_HR";
    private static final String ENTITY = "Holiday";
    private static final Set<String> VALID_TYPES = Set.of(
            "PUBLIC", "RELIGIOUS", "MOURNING", "COMPANY");

    private final HolidayRepository repository;
    private final AuditService audit;
    private final CurrentRequest currentRequest;
    private final String defaultJurisdiction;

    public HolidayService(HolidayRepository repository,
                          AuditService audit,
                          CurrentRequest currentRequest,
                          @Value("${hcm.holiday.default-jurisdiction:AZ}") String defaultJurisdiction) {
        this.repository = repository;
        this.audit = audit;
        this.currentRequest = currentRequest;
        this.defaultJurisdiction = defaultJurisdiction;
    }

    // ---------------------------------------------------------------- lookups

    /** Hot-path lookup against the default jurisdiction. */
    @Transactional(readOnly = true)
    public boolean isHoliday(LocalDate date) {
        return isHoliday(date, defaultJurisdiction);
    }

    @Transactional(readOnly = true)
    public boolean isHoliday(LocalDate date, String jurisdiction) {
        return repository.findByJurisdictionAndHolidayDate(jurisdiction, date)
                .map(Holiday::isActive)
                .orElse(false);
    }

    /**
     * Materialises the holiday dates in a year-month as a {@code Set}
     * so per-day callers get O(1) lookups. Pre-fetch once outside a
     * loop rather than calling {@link #isHoliday(LocalDate)} per day —
     * the timesheet engine in particular iterates 28-31 dates per
     * employee per month.
     */
    @Transactional(readOnly = true)
    public Set<LocalDate> holidayDatesIn(YearMonth month) {
        return holidayDatesBetween(month.atDay(1), month.atEndOfMonth());
    }

    @Transactional(readOnly = true)
    public Set<LocalDate> holidayDatesBetween(LocalDate start, LocalDate end) {
        return holidayDatesBetween(start, end, defaultJurisdiction);
    }

    @Transactional(readOnly = true)
    public Set<LocalDate> holidayDatesBetween(LocalDate start, LocalDate end, String jurisdiction) {
        if (start.isAfter(end)) return Set.of();
        Set<LocalDate> out = new HashSet<>();
        for (Holiday h : repository.activeBetween(jurisdiction, start, end)) {
            out.add(h.getHolidayDate());
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<Holiday> list(Integer year, Integer month, String jurisdiction) {
        String jur = jurisdiction == null || jurisdiction.isBlank()
                ? defaultJurisdiction
                : jurisdiction.toUpperCase();
        if (year == null && month == null) {
            return repository.findAllForJurisdiction(jur);
        }
        int y = year != null ? year : Year.now().getValue();
        if (month != null) {
            YearMonth ym = YearMonth.of(y, month);
            return repository.activeBetween(jur, ym.atDay(1), ym.atEndOfMonth());
        }
        return repository.activeBetween(jur,
                LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31));
    }

    @Transactional(readOnly = true)
    public Holiday get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday not found: " + id));
    }

    // ----------------------------------------------------------------- writes

    @Transactional
    public Holiday create(HolidayRequest req) {
        validate(req);
        String jur = req.jurisdiction() == null ? defaultJurisdiction
                : req.jurisdiction().toUpperCase();
        if (repository.findByJurisdictionAndHolidayDate(jur, req.holidayDate()).isPresent()) {
            throw new BadRequestException(
                    "A holiday already exists for " + jur + " on " + req.holidayDate());
        }
        Holiday h = new Holiday();
        apply(h, req, jur);
        h.setCreatedBy(currentRequest.username());
        h.setUpdatedBy(currentRequest.username());
        Holiday saved = repository.save(h);
        audit.record(MODULE, ENTITY, saved.getId().toString(),
                "CREATE", null, HolidayResponse.from(saved));
        return saved;
    }

    @Transactional
    public Holiday update(UUID id, HolidayRequest req) {
        validate(req);
        Holiday h = get(id);
        HolidayResponse before = HolidayResponse.from(h);
        String jur = req.jurisdiction() == null ? defaultJurisdiction
                : req.jurisdiction().toUpperCase();
        // Date / jurisdiction change must not collide with another row.
        if ((!jur.equals(h.getJurisdiction()) || !req.holidayDate().equals(h.getHolidayDate()))
                && repository.findByJurisdictionAndHolidayDate(jur, req.holidayDate())
                        .filter(other -> !other.getId().equals(id))
                        .isPresent()) {
            throw new BadRequestException(
                    "Another holiday already exists for " + jur + " on " + req.holidayDate());
        }
        apply(h, req, jur);
        h.setUpdatedBy(currentRequest.username());
        Holiday saved = repository.save(h);
        audit.record(MODULE, ENTITY, id.toString(),
                "UPDATE", before, HolidayResponse.from(saved));
        return saved;
    }

    @Transactional
    public void delete(UUID id) {
        Holiday h = get(id);
        HolidayResponse before = HolidayResponse.from(h);
        repository.delete(h);
        audit.record(MODULE, ENTITY, id.toString(),
                "DELETE", before, null);
    }

    // ----------------------------------------------------------------- helper

    private void apply(Holiday h, HolidayRequest req, String jurisdiction) {
        h.setJurisdiction(jurisdiction);
        h.setHolidayDate(req.holidayDate());
        h.setName(req.name());
        h.setHolidayType(req.holidayType() == null ? "PUBLIC"
                : req.holidayType().toUpperCase());
        h.setRecurring(req.recurring() == null ? true : req.recurring());
        h.setNotes(req.notes());
        h.setActive(req.active() == null ? true : req.active());
    }

    private void validate(HolidayRequest req) {
        if (req.holidayType() != null
                && !VALID_TYPES.contains(req.holidayType().toUpperCase())) {
            throw new BadRequestException(
                    "Unsupported holidayType (expected one of " + VALID_TYPES + "): "
                            + req.holidayType());
        }
    }

    /** Helper for downstream callers that want to filter a list of dates. */
    public Set<LocalDate> intersect(Set<LocalDate> dates, String jurisdiction) {
        if (dates.isEmpty()) return Set.of();
        LocalDate min = dates.stream().min(LocalDate::compareTo).orElseThrow();
        LocalDate max = dates.stream().max(LocalDate::compareTo).orElseThrow();
        Set<LocalDate> all = holidayDatesBetween(min, max, jurisdiction);
        return dates.stream().filter(all::contains).collect(Collectors.toSet());
    }
}
