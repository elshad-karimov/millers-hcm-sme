package az.millers.hcm.leave.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.attendance.domain.DailySummary;
import az.millers.hcm.attendance.domain.SummaryStatus;
import az.millers.hcm.attendance.repo.DailySummaryRepository;
import az.millers.hcm.audit.AuditService;
import az.millers.hcm.common.BadRequestException;
import az.millers.hcm.common.ResourceNotFoundException;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.leave.api.dto.AbsenceConvertRequest;
import az.millers.hcm.leave.api.dto.AbsenceDismissRequest;
import az.millers.hcm.leave.api.dto.AbsenceScanResult;
import az.millers.hcm.leave.domain.AbsenceConversionStatus;
import az.millers.hcm.leave.domain.LeaveRequest;
import az.millers.hcm.leave.domain.LeaveRequestStatus;
import az.millers.hcm.leave.domain.LeaveType;
import az.millers.hcm.leave.domain.UnauthorizedAbsenceConversion;
import az.millers.hcm.leave.repo.LeaveRequestRepository;
import az.millers.hcm.leave.repo.LeaveTypeRepository;
import az.millers.hcm.leave.repo.UnauthorizedAbsenceConversionRepository;
import az.millers.hcm.security.CurrentRequest;

@Service
public class UnauthorizedAbsenceService {

    private static final String MODULE = "LEAVE";
    private static final String ENTITY = "UnauthorizedAbsence";

    private final UnauthorizedAbsenceConversionRepository conversions;
    private final DailySummaryRepository dailySummaries;
    private final LeaveRequestRepository leaveRequests;
    private final LeaveTypeRepository leaveTypes;
    private final LeaveBalanceService balances;
    private final EmployeeRepository employees;
    private final AuditService audit;
    private final CurrentRequest currentRequest;

    public UnauthorizedAbsenceService(UnauthorizedAbsenceConversionRepository conversions,
                                       DailySummaryRepository dailySummaries,
                                       LeaveRequestRepository leaveRequests,
                                       LeaveTypeRepository leaveTypes,
                                       LeaveBalanceService balances,
                                       EmployeeRepository employees,
                                       AuditService audit,
                                       CurrentRequest currentRequest) {
        this.conversions = conversions;
        this.dailySummaries = dailySummaries;
        this.leaveRequests = leaveRequests;
        this.leaveTypes = leaveTypes;
        this.balances = balances;
        this.employees = employees;
        this.audit = audit;
        this.currentRequest = currentRequest;
    }

    /**
     * Scans the date range for ABSENT daily-summary rows that have not yet been
     * converted or dismissed. Also returns already-decided rows so HR can review.
     */
    @Transactional
    public List<AbsenceScanResult> scan(UUID employeeId, LocalDate from, LocalDate to) {
        Employee emp = employees.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));
        String empName = emp.getFirstName() + " " + emp.getLastName();
        String empNo = emp.getEmployeeNo();

        // Find all ABSENT days in range from attendance engine
        List<DailySummary> absent = dailySummaries
                .findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(employeeId, from, to)
                .stream()
                .filter(s -> s.getStatus() == SummaryStatus.ABSENT)
                .toList();

        // Build a map of existing conversion records
        Map<LocalDate, UnauthorizedAbsenceConversion> existing = conversions
                .findByEmployeeAndRange(employeeId, from, to)
                .stream()
                .collect(Collectors.toMap(UnauthorizedAbsenceConversion::getAbsenceDate, c -> c));

        List<AbsenceScanResult> results = new ArrayList<>();

        for (DailySummary ds : absent) {
            UnauthorizedAbsenceConversion conv = existing.computeIfAbsent(ds.getWorkDate(), date -> {
                UnauthorizedAbsenceConversion c = new UnauthorizedAbsenceConversion();
                c.setEmployeeId(employeeId);
                c.setAbsenceDate(date);
                c.setCreatedBy(currentRequest.username());
                return conversions.save(c);
            });
            LeaveType lt = conv.getLeaveTypeId() != null
                    ? leaveTypes.findById(conv.getLeaveTypeId()).orElse(null) : null;
            results.add(AbsenceScanResult.of(conv, empName, empNo,
                    lt != null ? lt.getName() : null));
        }

        // Also include already-resolved records in the range for visibility
        for (Map.Entry<LocalDate, UnauthorizedAbsenceConversion> e : existing.entrySet()) {
            if (results.stream().noneMatch(r -> r.absenceDate().equals(e.getKey()))) {
                LeaveType lt = e.getValue().getLeaveTypeId() != null
                        ? leaveTypes.findById(e.getValue().getLeaveTypeId()).orElse(null) : null;
                results.add(AbsenceScanResult.of(e.getValue(), empName, empNo,
                        lt != null ? lt.getName() : null));
            }
        }

        results.sort((a, b) -> b.absenceDate().compareTo(a.absenceDate()));
        return results;
    }

    /**
     * Lists all PENDING absence-conversion records (global view for HR workspace).
     */
    @Transactional(readOnly = true)
    public List<AbsenceScanResult> listPending() {
        return conversions.findByStatus(AbsenceConversionStatus.PENDING).stream()
                .map(c -> {
                    Employee emp = employees.findById(c.getEmployeeId()).orElse(null);
                    String name = emp != null ? emp.getFirstName() + " " + emp.getLastName() : "Unknown";
                    String no = emp != null ? emp.getEmployeeNo() : "";
                    LeaveType lt = c.getLeaveTypeId() != null
                            ? leaveTypes.findById(c.getLeaveTypeId()).orElse(null) : null;
                    return AbsenceScanResult.of(c, name, no, lt != null ? lt.getName() : null);
                })
                .toList();
    }

    /**
     * Converts unauthorized absences to approved leave retroactively.
     * Creates one LeaveRequest per contiguous block of dates (or one per date if non-contiguous).
     * The request is immediately APPROVED — no workflow, since this is an HR correction.
     */
    @Transactional
    public List<AbsenceScanResult> convert(AbsenceConvertRequest req) {
        LeaveType type = leaveTypes.findById(req.leaveTypeId())
                .orElseThrow(() -> new BadRequestException("Leave type not found: " + req.leaveTypeId()));
        Employee emp = employees.findById(req.employeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + req.employeeId()));

        List<LocalDate> sorted = req.absenceDates().stream().sorted().distinct().toList();
        if (sorted.isEmpty()) throw new BadRequestException("No dates provided");

        // Group contiguous dates into ranges
        List<LocalDate[]> ranges = groupContiguous(sorted);
        List<AbsenceScanResult> results = new ArrayList<>();

        for (LocalDate[] range : ranges) {
            LocalDate start = range[0];
            LocalDate end = range[1];
            BigDecimal totalDays = BigDecimal.valueOf(countDates(sorted, start, end));

            LeaveRequest lr = new LeaveRequest();
            lr.setRequestNo(String.format("LR-%05d", leaveRequests.nextRequestNoSequence()));
            lr.setEmployeeId(req.employeeId());
            lr.setLeaveTypeId(req.leaveTypeId());
            lr.setStartDate(start);
            lr.setEndDate(end);
            lr.setHalfDay(false);
            lr.setTotalDays(totalDays);
            lr.setReason("Unauthorized absence conversion" + (req.notes() != null ? ": " + req.notes() : ""));
            lr.setStatus(LeaveRequestStatus.APPROVED);
            lr.setCreatedBy(currentRequest.username());
            LeaveRequest savedLr = leaveRequests.save(lr);

            // Commit the balance directly (reserve + commit in one step for retroactive approval)
            balances.reserve(req.employeeId(), req.leaveTypeId(), start.getYear(), totalDays, type);
            balances.commit(req.employeeId(), req.leaveTypeId(), start.getYear(), totalDays);

            audit.record(MODULE, ENTITY, savedLr.getId().toString(), "CONVERTED",
                    null, Map.of("leaveType", type.getCode(), "days", totalDays.toPlainString(),
                            "period", start + " to " + end));

            // Update each absence-conversion record for dates in this range
            for (LocalDate date : sorted) {
                if (!date.isBefore(start) && !date.isAfter(end)) {
                    UnauthorizedAbsenceConversion conv = conversions
                            .findByEmployeeIdAndAbsenceDate(req.employeeId(), date)
                            .orElseGet(() -> {
                                UnauthorizedAbsenceConversion c = new UnauthorizedAbsenceConversion();
                                c.setEmployeeId(req.employeeId());
                                c.setAbsenceDate(date);
                                c.setCreatedBy(currentRequest.username());
                                return c;
                            });
                    conv.setStatus(AbsenceConversionStatus.CONVERTED);
                    conv.setLeaveTypeId(req.leaveTypeId());
                    conv.setLeaveRequestId(savedLr.getId());
                    conv.setNotes(req.notes());
                    conv.setResolvedBy(currentRequest.username());
                    conv.setResolvedAt(OffsetDateTime.now());
                    UnauthorizedAbsenceConversion savedConv = conversions.save(conv);
                    results.add(AbsenceScanResult.of(savedConv,
                            emp.getFirstName() + " " + emp.getLastName(), emp.getEmployeeNo(),
                            type.getName()));
                }
            }
        }
        return results;
    }

    /**
     * Formally dismisses unauthorized absences (records the HR decision without leave conversion).
     */
    @Transactional
    public List<AbsenceScanResult> dismiss(AbsenceDismissRequest req) {
        Employee emp = employees.findById(req.employeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + req.employeeId()));
        List<AbsenceScanResult> results = new ArrayList<>();
        for (LocalDate date : req.absenceDates()) {
            UnauthorizedAbsenceConversion conv = conversions
                    .findByEmployeeIdAndAbsenceDate(req.employeeId(), date)
                    .orElseGet(() -> {
                        UnauthorizedAbsenceConversion c = new UnauthorizedAbsenceConversion();
                        c.setEmployeeId(req.employeeId());
                        c.setAbsenceDate(date);
                        c.setCreatedBy(currentRequest.username());
                        return c;
                    });
            if (conv.getStatus() == AbsenceConversionStatus.CONVERTED) {
                throw new BadRequestException("Date " + date + " is already converted to leave");
            }
            conv.setStatus(AbsenceConversionStatus.DISMISSED);
            conv.setNotes(req.notes());
            conv.setResolvedBy(currentRequest.username());
            conv.setResolvedAt(OffsetDateTime.now());
            UnauthorizedAbsenceConversion saved = conversions.save(conv);
            audit.record(MODULE, ENTITY, saved.getId().toString(), "DISMISSED",
                    null, Map.of("date", date.toString(), "notes", req.notes() != null ? req.notes() : ""));
            results.add(AbsenceScanResult.of(saved,
                    emp.getFirstName() + " " + emp.getLastName(), emp.getEmployeeNo(), null));
        }
        return results;
    }

    private static List<LocalDate[]> groupContiguous(List<LocalDate> sorted) {
        List<LocalDate[]> groups = new ArrayList<>();
        if (sorted.isEmpty()) return groups;
        LocalDate start = sorted.get(0), prev = start;
        for (int i = 1; i < sorted.size(); i++) {
            LocalDate cur = sorted.get(i);
            if (!cur.equals(prev.plusDays(1))) {
                groups.add(new LocalDate[]{start, prev});
                start = cur;
            }
            prev = cur;
        }
        groups.add(new LocalDate[]{start, prev});
        return groups;
    }

    private static long countDates(List<LocalDate> sorted, LocalDate start, LocalDate end) {
        return sorted.stream().filter(d -> !d.isBefore(start) && !d.isAfter(end)).count();
    }
}
