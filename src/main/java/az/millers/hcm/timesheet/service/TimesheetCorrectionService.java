package az.millers.hcm.timesheet.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.security.CurrentRequest;
import az.millers.hcm.security.scope.AccessScopeService;
import az.millers.hcm.timesheet.api.dto.ApprovalDtos.CorrectionDecision;
import az.millers.hcm.timesheet.api.dto.ApprovalDtos.CorrectionRequestInput;
import az.millers.hcm.timesheet.api.dto.ApprovalDtos.CorrectionView;
import az.millers.hcm.timesheet.domain.CorrectionStatus;
import az.millers.hcm.timesheet.domain.DayApprovalState;
import az.millers.hcm.timesheet.domain.DayQuantity;
import az.millers.hcm.timesheet.domain.Timesheet;
import az.millers.hcm.timesheet.domain.TimesheetCorrectionRequest;
import az.millers.hcm.timesheet.domain.TimesheetDay;
import az.millers.hcm.timesheet.domain.TimesheetStatus;
import az.millers.hcm.timesheet.repo.DayQuantityRepository;
import az.millers.hcm.timesheet.repo.TimesheetCorrectionRequestRepository;
import az.millers.hcm.timesheet.repo.TimesheetDayRepository;
import az.millers.hcm.timesheet.repo.TimesheetRepository;

/**
 * Changing a day in a month that is already approved or locked.
 *
 * <p>The alternative — editing a closed month in place — destroys the record of
 * what was approved, which is the only thing an approval is worth. So a change
 * is a request naming the day, what it says now, what it should say and why;
 * approving it re-opens exactly that day and nothing else.
 *
 * <p>Once slice 3 exists and a period has been paid, this same request is the
 * hook for a retro adjustment rather than a rewrite of history.
 */
@Service
public class TimesheetCorrectionService {

    private static final String MODULE = "TIMESHEET";
    private static final String ENTITY = "TimesheetCorrection";

    /** States in which a day is settled and can only change through a request. */
    private static final Set<TimesheetStatus> SETTLED =
            Set.of(TimesheetStatus.APPROVED, TimesheetStatus.LOCKED);

    private final TimesheetCorrectionRequestRepository requests;
    private final TimesheetRepository timesheets;
    private final TimesheetDayRepository days;
    private final DayQuantityRepository quantities;
    private final EmployeeRepository employees;
    private final AccessScopeService accessScope;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public TimesheetCorrectionService(TimesheetCorrectionRequestRepository requests,
                                      TimesheetRepository timesheets,
                                      TimesheetDayRepository days,
                                      DayQuantityRepository quantities,
                                      EmployeeRepository employees,
                                      AccessScopeService accessScope,
                                      AuditService audit,
                                      CurrentRequest currentRequest) {
        this.requests = requests;
        this.timesheets = timesheets;
        this.days = days;
        this.quantities = quantities;
        this.employees = employees;
        this.accessScope = accessScope;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    // ---------- Employee raises one ----------

    @Transactional
    public CorrectionView request(UUID employeeId, int year, int month, CorrectionRequestInput in) {
        if (in == null || in.date() == null) {
            throw new BadRequestException("Name the day you want corrected.");
        }
        if (in.requestedValue() == null || in.requestedValue().isBlank()) {
            throw new BadRequestException("Describe what the day should say.");
        }
        if (in.reason() == null || in.reason().isBlank()) {
            throw new BadRequestException("A reason is required for a correction.");
        }

        Timesheet ts = timesheets.findByEmployeeIdAndPeriodYearAndPeriodMonth(employeeId, year, month)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No timesheet for " + year + "-" + month + "."));
        if (!SETTLED.contains(ts.getStatus())) {
            throw new BadRequestException(
                    "This month is " + ts.getStatus() + " — edit the day directly instead of "
                            + "raising a correction request.");
        }

        TimesheetDay day = days.findByTimesheetIdOrderByWorkDateAsc(ts.getId()).stream()
                .filter(d -> d.getWorkDate().equals(in.date()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("No day " + in.date() + " in this timesheet."));

        TimesheetCorrectionRequest req = new TimesheetCorrectionRequest();
        req.setTimesheetId(ts.getId());
        req.setEmployeeId(employeeId);
        req.setWorkDate(in.date());
        req.setCurrentValue(describe(day));
        req.setRequestedValue(in.requestedValue());
        req.setReason(in.reason());
        req.setStatus(CorrectionStatus.PENDING);
        req.setRequestedBy(currentRequest.username());
        TimesheetCorrectionRequest saved = requests.save(req);

        audit.record(MODULE, ENTITY, saved.getId().toString(), "CORRECTION_REQUESTED", null,
                Map.of("date", in.date().toString(),
                        "current", req.getCurrentValue(),
                        "requested", in.requestedValue()));
        return toView(saved, null);
    }

    // ---------- Manager / HR decides ----------

    @Transactional
    public CorrectionView decide(UUID requestId, CorrectionDecision decision) {
        TimesheetCorrectionRequest req = requests.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Correction not found: " + requestId));
        if (!accessScope.isAccessible(req.getEmployeeId())) {
            throw new ResourceNotFoundException("Correction not found: " + requestId);
        }
        if (req.getStatus() != CorrectionStatus.PENDING) {
            throw new BadRequestException("This correction has already been " + req.getStatus() + ".");
        }
        if (decision == null) {
            throw new BadRequestException("A decision is required.");
        }

        req.setStatus(decision.approve() ? CorrectionStatus.APPROVED : CorrectionStatus.REJECTED);
        req.setDecidedBy(currentRequest.username());
        req.setDecidedAt(OffsetDateTime.now());
        req.setDecisionNote(decision.note());
        requests.save(req);

        if (decision.approve()) {
            reopenDay(req);
        }

        audit.record(MODULE, ENTITY, req.getId().toString(),
                decision.approve() ? "CORRECTION_APPROVED" : "CORRECTION_REJECTED",
                null,
                Map.of("date", req.getWorkDate().toString(),
                        "note", decision.note() == null ? "" : decision.note()));
        return toView(req, null);
    }

    /**
     * Re-open exactly the corrected day.
     *
     * <p>The month goes to REOPENED rather than DRAFT so it is visibly a
     * settled month being amended, not a fresh one — and every other day keeps
     * the APPROVED verdict it already earned.
     */
    private void reopenDay(TimesheetCorrectionRequest req) {
        Timesheet ts = timesheets.findById(req.getTimesheetId())
                .orElseThrow(() -> new ResourceNotFoundException("Timesheet not found"));

        days.findByTimesheetIdOrderByWorkDateAsc(ts.getId()).stream()
                .filter(d -> d.getWorkDate().equals(req.getWorkDate()))
                .findFirst()
                .ifPresent(day -> {
                    day.setApprovalState(DayApprovalState.RETURNED);
                    day.setReturnReason("Correction approved: " + req.getReason());
                    day.setReturnedBy(currentRequest.username());
                    day.setReturnedAt(OffsetDateTime.now());
                    days.save(day);
                });

        ts.setStatus(TimesheetStatus.REOPENED);
        ts.setLockedAt(null);
        timesheets.save(ts);
    }

    // ---------- Queries ----------

    @Transactional(readOnly = true)
    public List<CorrectionView> forTimesheet(UUID timesheetId) {
        return requests.findByTimesheetIdOrderByRequestedAtDesc(timesheetId).stream()
                .map(r -> toView(r, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CorrectionView> mine(UUID employeeId) {
        return requests.findByEmployeeIdOrderByRequestedAtDesc(employeeId).stream()
                .map(r -> toView(r, null))
                .toList();
    }

    /** Pending corrections the caller may decide — hierarchy-scoped. */
    @Transactional(readOnly = true)
    public List<CorrectionView> pending() {
        Set<UUID> scope = accessScope.scopeOrNullForCurrentUser();
        List<TimesheetCorrectionRequest> found;
        if (scope == null) {
            found = requests.findByStatusOrderByRequestedAtAsc(CorrectionStatus.PENDING);
        } else if (scope.isEmpty()) {
            return List.of();
        } else {
            found = requests.findByStatusAndEmployeeIdInOrderByRequestedAtAsc(
                    CorrectionStatus.PENDING, scope);
        }
        Map<UUID, Employee> byId = employees.findAllById(
                        found.stream().map(TimesheetCorrectionRequest::getEmployeeId)
                                .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Employee::getId, e -> e, (a, b) -> a));
        return found.stream().map(r -> toView(r, byId.get(r.getEmployeeId()))).toList();
    }

    // ---------- Helpers ----------

    /** A human-readable snapshot of what a day currently says. */
    private String describe(TimesheetDay day) {
        List<DayQuantity> qs = quantities.findByTimesheetDayId(day.getId());
        String type = day.getWorkType() == null ? "no work type" : day.getWorkType().name();
        if (qs.isEmpty()) return type + ", nothing recorded";
        String detail = qs.stream()
                .filter(q -> q.getQuantity().signum() > 0)
                .map(q -> q.getCategoryCode() + " " + q.getQuantity().toPlainString())
                .collect(Collectors.joining(", "));
        return type + ": " + detail;
    }

    private CorrectionView toView(TimesheetCorrectionRequest r, Employee e) {
        return new CorrectionView(
                r.getId(), r.getTimesheetId(), r.getEmployeeId(),
                e == null ? null : e.getLastName() + ", " + e.getFirstName(),
                r.getWorkDate(), r.getCurrentValue(), r.getRequestedValue(), r.getReason(),
                r.getStatus().name(), r.getRequestedBy(), r.getRequestedAt(),
                r.getDecidedBy(), r.getDecidedAt(), r.getDecisionNote());
    }

    /** Convenience for callers that only have a date. */
    public static LocalDate parseDate(String raw) {
        return LocalDate.parse(raw);
    }
}
