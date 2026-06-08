package az.millers.hcm.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

import az.millers.hcm.corehr.domain.EmploymentStatus;
import az.millers.hcm.corehr.repo.EmployeeRepository;
import az.millers.hcm.workflow.repo.WorkflowInstanceRepository;

/**
 * Custom Micrometer gauges for HCM business metrics (M160 / §18.3).
 *
 * <p>Metrics exposed at {@code /actuator/prometheus}:
 * <ul>
 *   <li>{@code hcm_employees_active} — active headcount</li>
 *   <li>{@code hcm_employees_on_probation} — employees in probation</li>
 *   <li>{@code hcm_workflow_pending} — approvals waiting in inbox</li>
 * </ul>
 *
 * <p>Gauges use lazy suppliers (evaluated by Prometheus scrape) so the DB
 * is not queried at startup — only when the metrics endpoint is polled.
 */
@Component
public class HcmMetricsBinder implements MeterBinder {

    private final EmployeeRepository employees;
    private final WorkflowInstanceRepository workflowInstances;

    public HcmMetricsBinder(EmployeeRepository employees,
                             WorkflowInstanceRepository workflowInstances) {
        this.employees         = employees;
        this.workflowInstances = workflowInstances;
    }

    @Override
    public void bindTo(MeterRegistry registry) {

        Gauge.builder("hcm.employees.active",
                        employees, r -> r.countByEmploymentStatus(EmploymentStatus.ACTIVE))
                .description("Number of currently active employees")
                .register(registry);

        Gauge.builder("hcm.employees.on_probation",
                        employees, r -> r.countByEmploymentStatus(EmploymentStatus.ON_PROBATION))
                .description("Number of employees on probation")
                .register(registry);

        Gauge.builder("hcm.workflow.pending",
                        workflowInstances, r -> r.countByStatus("PENDING"))
                .description("Number of workflow instances awaiting approval")
                .register(registry);
    }
}
