package az.millers.hcm.timesheet.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import az.millers.hcm.corehr.service.HolidayService;

import az.millers.hcm.attendance.domain.DailySummary;
import az.millers.hcm.attendance.domain.ScheduleAssignment;
import az.millers.hcm.attendance.domain.SummaryStatus;
import az.millers.hcm.attendance.domain.WorkSchedule;
import az.millers.hcm.attendance.repo.DailySummaryRepository;
import az.millers.hcm.attendance.repo.ScheduleAssignmentRepository;
import az.millers.hcm.attendance.repo.WorkScheduleRepository;
import az.millers.hcm.businesstrip.domain.BusinessTripRequest;
import az.millers.hcm.businesstrip.repo.BusinessTripRequestRepository;
import az.millers.hcm.leave.domain.LeaveRequest;
import az.millers.hcm.leave.domain.LeaveType;
import az.millers.hcm.leave.repo.LeaveRequestRepository;
import az.millers.hcm.leave.repo.LeaveTypeRepository;
import az.millers.hcm.permission.domain.PermissionRequest;
import az.millers.hcm.permission.repo.PermissionRequestRepository;
import az.millers.hcm.timesheet.domain.TimesheetDay;

/**
 * Builds the per-day rows for a monthly timesheet (PRD 8.8.1 / 8.8.6).
 *
 * <p>Picks exactly one primary code per day from the priority chain
 * {@code BT > L > S > P > W > A > H}. Anomalies (overlapping work + leave,
 * missing punch, schedule conflict) are recorded for HR review.
 */
@Component
public class TimesheetGenerator {

    private static final BigDecimal MINUTES_PER_HOUR = BigDecimal.valueOf(60);

    private final ScheduleAssignmentRepository assignments;
    private final WorkScheduleRepository schedules;
    private final DailySummaryRepository summaries;
    private final LeaveRequestRepository leaves;
    private final LeaveTypeRepository leaveTypes;
    private final BusinessTripRequestRepository trips;
    private final PermissionRequestRepository permissions;
    private final HolidayService holidays;

    public TimesheetGenerator(ScheduleAssignmentRepository assignments,
                               WorkScheduleRepository schedules,
                               DailySummaryRepository summaries,
                               LeaveRequestRepository leaves,
                               LeaveTypeRepository leaveTypes,
                               BusinessTripRequestRepository trips,
                               PermissionRequestRepository permissions,
                               HolidayService holidays) {
        this.assignments = assignments;
        this.schedules = schedules;
        this.summaries = summaries;
        this.leaves = leaves;
        this.leaveTypes = leaveTypes;
        this.trips = trips;
        this.permissions = permissions;
        this.holidays = holidays;
    }

    public List<TimesheetDay> buildDays(UUID employeeId, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        Map<UUID, LeaveType> typesById = new LinkedHashMap<>();
        leaveTypes.findAll().forEach(t -> typesById.put(t.getId(), t));

        List<LeaveRequest> approvedLeaves = leaves.findApprovedOverlapping(employeeId, from, to);
        List<BusinessTripRequest> approvedTrips = trips.findApprovedOverlapping(employeeId, from, to);
        List<PermissionRequest> approvedPermissions = permissions.findApprovedInRange(employeeId, from, to);
        // M38: pre-fetch the month's holidays once into a Set so the
        // per-day H-code check is O(1). Lookup runs against the default
        // jurisdiction — multi-country tenants can override via the
        // hcm.holiday.default-jurisdiction property.
        Set<LocalDate> holidayDates = holidays.holidayDatesIn(ym);

        List<TimesheetDay> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            out.add(buildOneDay(employeeId, d, approvedLeaves, approvedTrips,
                    approvedPermissions, typesById, holidayDates));
        }
        return out;
    }

    private TimesheetDay buildOneDay(UUID employeeId, LocalDate date,
                                      List<LeaveRequest> leavesList,
                                      List<BusinessTripRequest> tripsList,
                                      List<PermissionRequest> permissionsList,
                                      Map<UUID, LeaveType> typesById,
                                      Set<LocalDate> holidayDates) {
        TimesheetDay row = new TimesheetDay();
        row.setWorkDate(date);

        // Schedule context for the day.
        Optional<ScheduleAssignment> assignment = assignments.findActiveOn(employeeId, date);
        WorkSchedule schedule = assignment
                .flatMap(a -> schedules.findById(a.getScheduleId()))
                .orElse(null);
        boolean workingDay = schedule != null && schedule.isWorkingDay(date.getDayOfWeek());
        boolean isHoliday = holidayDates.contains(date);

        Optional<DailySummary> attendance = summaries.findByEmployeeIdAndWorkDate(employeeId, date);
        attendance.ifPresent(a -> row.setSourceSummaryId(a.getId()));

        // BT — highest priority.
        BusinessTripRequest bt = firstMatchingTrip(date, tripsList);
        // Leave (annual / other) — second.
        LeaveRequest leave = firstMatchingLeave(date, leavesList);
        // Permission — third (sub-daily; only "wins" when there's no work).
        PermissionRequest permission = firstMatchingPermission(date, permissionsList);

        List<String> anomalies = new ArrayList<>();
        if (bt != null && leave != null) anomalies.add("BT_AND_LEAVE");
        if (bt != null && attendance.isPresent()
                && attendance.get().getWorkedMinutes() > 0) anomalies.add("BT_WITH_WORK");
        if (leave != null && attendance.isPresent()
                && attendance.get().getWorkedMinutes() > 0) anomalies.add("LEAVE_WITH_WORK");
        if (attendance.isPresent() && attendance.get().getStatus() == SummaryStatus.PARTIAL) {
            anomalies.add("MISSING_PUNCH");
        }

        if (bt != null) {
            row.setPrimaryCode("BT");
            row.setBtRequestId(bt.getId());
        } else if (leave != null) {
            row.setPrimaryCode(codeForLeave(leave, typesById));
            row.setLeaveRequestId(leave.getId());
        } else if (attendance.isPresent()
                && attendance.get().getWorkedMinutes() > 0) {
            row.setPrimaryCode("W");
            apply(row, attendance.get());
            if (permission != null) {
                // Permission overlaps with work day — keep W as primary, log conflict + record link.
                anomalies.add("PERMISSION_OVERLAPS_WORK");
                row.setPermissionRequestId(permission.getId());
            }
            // M38: working on a holiday is allowed but flagged so the
            // payroll OT engine (future) can pick it up for the
            // holiday-rate multiplier. Keeps W as the primary code so
            // the employee still gets paid for their hours.
            if (isHoliday) anomalies.add("WORKED_ON_HOLIDAY");
        } else if (permission != null) {
            row.setPrimaryCode("P");
            row.setPermissionRequestId(permission.getId());
        } else if (isHoliday) {
            // M38: holiday wins over A (absent) — a public holiday that
            // falls on a scheduled working weekday is paid time off,
            // not an unexcused absence. Pre-M38 this branch fired only
            // when (schedule == null || !workingDay), i.e. weekends.
            row.setPrimaryCode("H");
        } else if (workingDay) {
            row.setPrimaryCode("A");
        } else {
            row.setPrimaryCode("H");
        }

        if (!anomalies.isEmpty()) {
            row.setAnomalies(String.join(",", anomalies));
        }
        return row;
    }

    private BusinessTripRequest firstMatchingTrip(LocalDate date, List<BusinessTripRequest> all) {
        return all.stream()
                .filter(t -> !date.isBefore(t.getStartDate()) && !date.isAfter(t.getEndDate()))
                .findFirst().orElse(null);
    }

    private LeaveRequest firstMatchingLeave(LocalDate date, List<LeaveRequest> all) {
        return all.stream()
                .filter(r -> !date.isBefore(r.getStartDate()) && !date.isAfter(r.getEndDate()))
                .findFirst().orElse(null);
    }

    private PermissionRequest firstMatchingPermission(LocalDate date, List<PermissionRequest> all) {
        return all.stream()
                .filter(p -> p.getPermissionDate().equals(date))
                .findFirst().orElse(null);
    }

    private String codeForLeave(LeaveRequest r, Map<UUID, LeaveType> typesById) {
        LeaveType t = typesById.get(r.getLeaveTypeId());
        if (t != null && "SICK".equalsIgnoreCase(t.getCode())) return "S";
        return "L";
    }

    private void apply(TimesheetDay row, DailySummary summary) {
        row.setWorkedHours(toHours(summary.getWorkedMinutes()));
        row.setOvertimeHours(toHours(summary.getOvertimeMinutes()));
        row.setBreakHours(toHours(summary.getBreakMinutes()));
        row.setLateMinutes(summary.getLateMinutes());
        row.setEarlyMinutes(summary.getEarlyMinutes());
    }

    private BigDecimal toHours(int minutes) {
        if (minutes == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(minutes).divide(MINUTES_PER_HOUR, 2, RoundingMode.HALF_UP);
    }

    public BigDecimal permissionHoursForDay(TimesheetDay day, List<PermissionRequest> approved) {
        if (day.getPermissionRequestId() == null) return BigDecimal.ZERO;
        return approved.stream()
                .filter(p -> p.getId().equals(day.getPermissionRequestId()))
                .map(PermissionRequest::getDurationHours)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }
}
