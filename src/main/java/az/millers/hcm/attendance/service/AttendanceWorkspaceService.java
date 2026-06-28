package az.millers.hcm.attendance.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.attendance.api.dto.WorkspaceDtos.AttendanceWorkspaceSummary;
import az.millers.hcm.attendance.api.dto.WorkspaceDtos.WorkspaceEmployeeRow;
import az.millers.hcm.attendance.domain.DailySummary;
import az.millers.hcm.attendance.repo.AttendanceCorrectionRepository;
import az.millers.hcm.attendance.repo.AttendanceExceptionRepository;
import az.millers.hcm.attendance.repo.DailySummaryRepository;
import az.millers.hcm.attendance.repo.OvertimeRequestRepository;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.organization.repo.LegalEntityRepository;

/**
 * M335: HR attendance workspace service.
 *
 * <p>Provides daily attendance workspace summary with counts, late/absent employees,
 * and pending items.
 */
@Service
@Transactional(readOnly = true)
public class AttendanceWorkspaceService {

    private final DailySummaryRepository summaryRepository;
    private final AttendanceCorrectionRepository correctionRepository;
    private final OvertimeRequestRepository overtimeRepository;
    private final AttendanceExceptionRepository exceptionRepository;
    private final EmployeeRepository employeeRepository;
    private final LegalEntityRepository legalEntities;

    public AttendanceWorkspaceService(DailySummaryRepository summaryRepository,
                                       AttendanceCorrectionRepository correctionRepository,
                                       OvertimeRequestRepository overtimeRepository,
                                       AttendanceExceptionRepository exceptionRepository,
                                       EmployeeRepository employeeRepository,
                                       LegalEntityRepository legalEntities) {
        this.summaryRepository = summaryRepository;
        this.correctionRepository = correctionRepository;
        this.overtimeRepository = overtimeRepository;
        this.exceptionRepository = exceptionRepository;
        this.employeeRepository = employeeRepository;
        this.legalEntities = legalEntities;
    }

    public AttendanceWorkspaceSummary getWorkspace(LocalDate date, UUID departmentId) {
        UUID tenantId = defaultTenantId();
        List<DailySummary> summaries = summaryRepository.findByEmployeeIdAndWorkDateBetweenOrderByWorkDateDesc(
                null, date, date);

        int totalEmployees = 0;
        int presentCount = 0;
        int absentCount = 0;
        int lateCount = 0;
        int missingClockOutCount = 0;

        List<WorkspaceEmployeeRow> lateEmployees = new ArrayList<>();
        List<WorkspaceEmployeeRow> absentEmployees = new ArrayList<>();

        for (DailySummary summary : summaries) {
            Employee employee = employeeRepository.findById(summary.getEmployeeId()).orElse(null);
            if (employee == null) continue;

            if (departmentId != null && !departmentId.equals(employee.getOrgUnitId())) {
                continue;
            }

            totalEmployees++;

            String status = summary.getStatus().name();
            if ("PRESENT".equals(status)) {
                presentCount++;
            } else if ("ABSENT".equals(status)) {
                absentCount++;
                absentEmployees.add(new WorkspaceEmployeeRow(
                        summary.getEmployeeId(),
                        employee.getFirstName() + " " + employee.getLastName(),
                        status,
                        0));
            }

            if (summary.getLateMinutes() > 0) {
                lateCount++;
                lateEmployees.add(new WorkspaceEmployeeRow(
                        summary.getEmployeeId(),
                        employee.getFirstName() + " " + employee.getLastName(),
                        status,
                        summary.getLateMinutes()));
            }

            if (summary.getExitTime() == null && !"ABSENT".equals(status)) {
                missingClockOutCount++;
            }
        }

        int pendingCorrections = (int) correctionRepository.findByTenantIdAndWorkflowStatusOrderByCreatedAtDesc(
                tenantId, "PENDING").size();

        int pendingOtRequests = (int) overtimeRepository.findByTenantIdAndWorkflowStatusOrderByCreatedAtDesc(
                tenantId, "PENDING").size();

        int openExceptions = (int) exceptionRepository.countByTenantIdAndStatus(tenantId, "OPEN");

        return new AttendanceWorkspaceSummary(
                date,
                totalEmployees,
                presentCount,
                absentCount,
                lateCount,
                missingClockOutCount,
                pendingCorrections,
                pendingOtRequests,
                (int) openExceptions,
                lateEmployees,
                absentEmployees);
    }

    private UUID defaultTenantId() {
        return legalEntities.findAllByOrderByCodeAsc().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No legal entity found")).getId();
    }
}
