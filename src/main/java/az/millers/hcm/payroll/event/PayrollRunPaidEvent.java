package az.millers.hcm.payroll.event;

import java.util.UUID;

/**
 * Published when a {@link az.millers.hcm.payroll.domain.PayrollRun} is
 * transitioned to {@code PAID} status.
 *
 * <p>Consumed by {@link az.millers.hcm.payroll.service.PayslipReadyNotificationListener}
 * to notify each employee that their payslip is available in the self-service
 * portal (M195).
 */
public record PayrollRunPaidEvent(
        UUID runId,
        String runCode,
        int periodYear,
        int periodMonth) {
}
