package az.millers.hcm.attendance.service;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

import az.millers.hcm.attendance.domain.ExceptionConfig;
import az.millers.hcm.attendance.events.*;
import az.millers.hcm.attendance.repo.ExceptionConfigRepository;
import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.notifications.NotificationService;

/**
 * M337: Attendance notification service.
 *
 * <p>Listens to attendance events and sends notifications via NotificationService.
 */
@Service
public class AttendanceNotificationService {

    private final NotificationService notificationService;
    private final EmployeeRepository employeeRepository;
    private final ExceptionConfigRepository exceptionConfigRepository;

    public AttendanceNotificationService(NotificationService notificationService,
                                          EmployeeRepository employeeRepository,
                                          ExceptionConfigRepository exceptionConfigRepository) {
        this.notificationService = notificationService;
        this.employeeRepository = employeeRepository;
        this.exceptionConfigRepository = exceptionConfigRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void onSummaryComputed(AttendanceSummaryComputedEvent event) {
        var summary = event.summary();
        Employee employee = employeeRepository.findById(summary.getEmployeeId()).orElse(null);
        if (employee == null) return;

        if (summary.getLateMinutes() > 10) {
            notificationService.createInApp(
                    employee.getUsername() != null ? employee.getUsername() : employee.getEmployeeNo(),
                    "Late Arrival Recorded",
                    "You were late by " + summary.getLateMinutes() + " minutes on " + summary.getWorkDate(),
                    "attendance",
                    "daily_summary",
                    summary.getId().toString());
        }

        if ("ABSENT".equals(summary.getStatus().name()) && employee.getManagerId() != null) {
            Employee manager = employeeRepository.findById(employee.getManagerId()).orElse(null);
            if (manager != null && manager.getUsername() != null) {
                notificationService.createInApp(
                        manager.getUsername(),
                        "Employee Absent",
                        employee.getFirstName() + " " + employee.getLastName() + " is absent on " + summary.getWorkDate(),
                        "attendance",
                        "daily_summary",
                        summary.getId().toString());
            }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void onCorrectionSubmitted(CorrectionSubmittedEvent event) {
        Employee employee = employeeRepository.findById(event.employeeId()).orElse(null);
        if (employee == null || employee.getManagerId() == null) return;

        Employee manager = employeeRepository.findById(employee.getManagerId()).orElse(null);
        if (manager != null && manager.getUsername() != null) {
            notificationService.createInApp(
                    manager.getUsername(),
                    "Attendance Correction Submitted",
                    employee.getFirstName() + " " + employee.getLastName() + " submitted an attendance correction",
                    "attendance",
                    "attendance_correction_request",
                    event.correctionId().toString());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void onCorrectionApproved(CorrectionApprovedEvent event) {
        Employee employee = employeeRepository.findById(event.employeeId()).orElse(null);
        if (employee == null) return;

        notificationService.createInApp(
                employee.getUsername() != null ? employee.getUsername() : employee.getEmployeeNo(),
                "Correction Approved",
                "Your attendance correction has been approved",
                "attendance",
                "attendance_correction_request",
                event.correctionId().toString());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void onCorrectionRejected(CorrectionRejectedEvent event) {
        Employee employee = employeeRepository.findById(event.employeeId()).orElse(null);
        if (employee == null) return;

        notificationService.createInApp(
                employee.getUsername() != null ? employee.getUsername() : employee.getEmployeeNo(),
                "Correction Rejected",
                "Your attendance correction has been rejected",
                "attendance",
                "attendance_correction_request",
                event.correctionId().toString());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void onOvertimeSubmitted(OvertimeRequestSubmittedEvent event) {
        Employee employee = employeeRepository.findById(event.employeeId()).orElse(null);
        if (employee == null || employee.getManagerId() == null) return;

        Employee manager = employeeRepository.findById(employee.getManagerId()).orElse(null);
        if (manager != null && manager.getUsername() != null) {
            notificationService.createInApp(
                    manager.getUsername(),
                    "Overtime Request Submitted",
                    employee.getFirstName() + " " + employee.getLastName()
                            + " requested " + event.requestedMinutes() + " min overtime",
                    "attendance",
                    "overtime_request",
                    event.requestId().toString());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void onOvertimeApproved(OvertimeApprovedEvent event) {
        Employee employee = employeeRepository.findById(event.employeeId()).orElse(null);
        if (employee == null) return;

        notificationService.createInApp(
                employee.getUsername() != null ? employee.getUsername() : employee.getEmployeeNo(),
                "Overtime Approved",
                "Your overtime request has been approved",
                "attendance",
                "overtime_request",
                event.requestId().toString());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void onPeriodLocked(PeriodLockedEvent event) {
        notificationService.createInApp(
                "hr-admin",
                "Attendance Period Locked",
                "Period " + event.year() + "-" + event.month() + " has been locked",
                "attendance",
                "attendance_period",
                event.year() + "-" + event.month());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void onExceptionGenerated(ExceptionGeneratedEvent event) {
        var exception = event.exception();

        ExceptionConfig config = exceptionConfigRepository.findByTenantIdAndExceptionType(
                event.tenantId(), exception.getExceptionType()).orElse(null);

        if (config == null || !config.isAutoNotify()) return;

        Employee employee = employeeRepository.findById(exception.getEmployeeId()).orElse(null);
        if (employee == null) return;

        if (employee.getManagerId() != null) {
            Employee manager = employeeRepository.findById(employee.getManagerId()).orElse(null);
            if (manager != null && manager.getUsername() != null) {
                notificationService.createInApp(
                        manager.getUsername(),
                        "Attendance Exception: " + exception.getExceptionType(),
                        employee.getFirstName() + " " + employee.getLastName()
                                + " triggered " + exception.getExceptionType() + " on " + exception.getWorkDate(),
                        "attendance",
                        "attendance_exception",
                        exception.getId().toString());
            }
        }
    }
}
