package az.millers.hcm.leave.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.leave.api.dto.LeaveWorkspaceStats;
import az.millers.hcm.leave.domain.LeaveRequest;
import az.millers.hcm.leave.domain.LeaveRequestStatus;
import az.millers.hcm.leave.domain.LeaveType;
import az.millers.hcm.leave.repo.LeaveRequestRepository;
import az.millers.hcm.leave.repo.LeaveTypeRepository;

@Service
public class LeaveWorkspaceService {

    private final LeaveRequestRepository requests;
    private final LeaveTypeRepository types;
    private final EmployeeRepository employees;

    public LeaveWorkspaceService(LeaveRequestRepository requests,
                                  LeaveTypeRepository types,
                                  EmployeeRepository employees) {
        this.requests = requests;
        this.types = types;
        this.employees = employees;
    }

    @Transactional(readOnly = true)
    public LeaveWorkspaceStats stats(int year) {
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd   = LocalDate.of(year, 12, 31);
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        LocalDate monthEnd   = monthStart.withDayOfMonth(monthStart.lengthOfMonth());

        // All requests in the year
        List<LeaveRequest> yearRequests = requests.findByStartDateBetween(yearStart, yearEnd);

        long pendingApprovals = yearRequests.stream()
                .filter(r -> r.getStatus() == LeaveRequestStatus.PENDING).count();

        long approvedThisMonth = yearRequests.stream()
                .filter(r -> r.getStatus() == LeaveRequestStatus.APPROVED
                        && !r.getStartDate().isBefore(monthStart)
                        && !r.getStartDate().isAfter(monthEnd)).count();

        long rejectedThisMonth = yearRequests.stream()
                .filter(r -> r.getStatus() == LeaveRequestStatus.REJECTED
                        && !r.getStartDate().isBefore(monthStart)
                        && !r.getStartDate().isAfter(monthEnd)).count();

        long cancelledThisMonth = yearRequests.stream()
                .filter(r -> r.getStatus() == LeaveRequestStatus.CANCELLED
                        && !r.getStartDate().isBefore(monthStart)
                        && !r.getStartDate().isAfter(monthEnd)).count();

        BigDecimal totalDays = yearRequests.stream()
                .filter(r -> r.getStatus() == LeaveRequestStatus.APPROVED)
                .map(r -> r.getTotalDays() != null ? r.getTotalDays() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // By-type breakdown
        Map<UUID, LeaveType> typeMap = types.findAll().stream()
                .collect(Collectors.toMap(LeaveType::getId, t -> t));

        List<LeaveWorkspaceStats.TypeBreakdown> byType = yearRequests.stream()
                .filter(r -> r.getStatus() == LeaveRequestStatus.APPROVED)
                .collect(Collectors.groupingBy(LeaveRequest::getLeaveTypeId))
                .entrySet().stream()
                .map(e -> {
                    LeaveType t = typeMap.get(e.getKey());
                    BigDecimal days = e.getValue().stream()
                            .map(r -> r.getTotalDays() != null ? r.getTotalDays() : BigDecimal.ZERO)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new LeaveWorkspaceStats.TypeBreakdown(
                            t != null ? t.getCode() : "?",
                            t != null ? t.getName() : "Unknown",
                            e.getValue().size(), days);
                })
                .sorted((a, b) -> b.totalDays().compareTo(a.totalDays()))
                .toList();

        // Monthly trend (all 12 months, only approved)
        List<LeaveWorkspaceStats.MonthlyTrend> monthlyTrend = yearRequests.stream()
                .filter(r -> r.getStatus() == LeaveRequestStatus.APPROVED)
                .collect(Collectors.groupingBy(r -> r.getStartDate().getMonthValue()))
                .entrySet().stream()
                .map(e -> new LeaveWorkspaceStats.MonthlyTrend(
                        year, e.getKey(),
                        e.getValue().size(),
                        e.getValue().stream()
                                .map(r -> r.getTotalDays() != null ? r.getTotalDays() : BigDecimal.ZERO)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)))
                .sorted(Comparator.comparingInt(LeaveWorkspaceStats.MonthlyTrend::month))
                .toList();

        // Top absence employees (most approved leave days)
        Map<UUID, BigDecimal> employeeDays = yearRequests.stream()
                .filter(r -> r.getStatus() == LeaveRequestStatus.APPROVED)
                .collect(Collectors.groupingBy(LeaveRequest::getEmployeeId,
                        Collectors.reducing(BigDecimal.ZERO,
                                r -> r.getTotalDays() != null ? r.getTotalDays() : BigDecimal.ZERO,
                                BigDecimal::add)));

        List<LeaveWorkspaceStats.AbsenceHotspot> hotspots = employeeDays.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(10)
                .map(e -> {
                    Employee emp = employees.findById(e.getKey()).orElse(null);
                    return new LeaveWorkspaceStats.AbsenceHotspot(
                            emp != null ? emp.getEmployeeNo() : "?",
                            emp != null ? emp.getFirstName() + " " + emp.getLastName() : "Unknown",
                            e.getValue().longValue());
                })
                .toList();

        return new LeaveWorkspaceStats(pendingApprovals, approvedThisMonth,
                rejectedThisMonth, cancelledThisMonth, totalDays, byType, monthlyTrend, hotspots);
    }
}
