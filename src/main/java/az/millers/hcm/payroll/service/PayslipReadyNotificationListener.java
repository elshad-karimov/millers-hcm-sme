package az.millers.hcm.payroll.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.notifications.NotificationService;
import az.millers.hcm.notifications.domain.NotificationCategory;
import az.millers.hcm.payroll.domain.PayrollResult;
import az.millers.hcm.payroll.event.PayrollRunPaidEvent;
import az.millers.hcm.payroll.repo.PayrollResultRepository;

/**
 * Notifies each employee that their payslip is available when a payroll
 * run is marked PAID (M195 / PRD §10.3 self-service payslip).
 *
 * <p>{@code @Async} ensures notification failures never roll back the
 * payroll PAID transaction. Failures are caught per-employee and logged.
 */
@Component
public class PayslipReadyNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(PayslipReadyNotificationListener.class);

    private final PayrollResultRepository results;
    private final EmployeeRepository employees;
    private final NotificationService notifications;

    public PayslipReadyNotificationListener(PayrollResultRepository results,
                                             EmployeeRepository employees,
                                             NotificationService notifications) {
        this.results = results;
        this.employees = employees;
        this.notifications = notifications;
    }

    @Async
    @EventListener
    public void onPayrollRunPaid(PayrollRunPaidEvent event) {
        List<PayrollResult> runResults = results.findByRunIdOrderByEmployeeIdAsc(event.runId());
        if (runResults.isEmpty()) return;

        String monthLabel = String.format("%d/%02d", event.periodYear(), event.periodMonth());
        String title = "Your payslip for " + monthLabel + " is ready";
        String body  = "Your payslip for " + monthLabel + " is now available. "
                + "Log in to the self-service portal to view and download your payslip.";

        int notified = 0;
        for (PayrollResult result : runResults) {
            employees.findById(result.getEmployeeId()).ifPresent(emp -> {
                String username = emp.getUsername();
                if (username == null || username.isBlank()) return;
                try {
                    notifications.notifyAll(
                            NotificationCategory.PAYSLIP_READY,
                            username, title, body,
                            "PAYROLL", "PayrollRun", event.runId().toString());
                } catch (Exception ex) {
                    log.warn("PayslipReadyNotificationListener: failed to notify {} for run {}: {}",
                            username, event.runId(), ex.getMessage());
                }
            });
            notified++;
        }
        log.info("PayslipReadyNotificationListener: notified {} employee(s) for run {} {}",
                notified, event.runCode(), monthLabel);
    }
}
