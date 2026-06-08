package az.millers.hcm.corehr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import az.millers.hcm.corehr.domain.Employee;
import az.millers.hcm.lifecycle.api.dto.ChecklistDtos.StartAssignmentRequest;
import az.millers.hcm.lifecycle.domain.ChecklistFlowType;
import az.millers.hcm.lifecycle.service.ChecklistService;

/**
 * Fires the onboarding workflow for a newly-created employee (PRD §8.1.4 / §8.10.6).
 *
 * <p>Auto-starts every active {@link ChecklistFlowType#ONBOARDING} checklist
 * template so that the HR team's task list is populated immediately — identical
 * to the path taken when a hire originates from the recruitment pipeline
 * (M159 / ApplicationService).
 *
 * <p>Non-fatal: if no ONBOARDING template is configured, or the checklist
 * service throws, the employee creation still succeeds and an error is logged.
 */
@Component
public class OnboardingWorkflow {

    private static final Logger log = LoggerFactory.getLogger(OnboardingWorkflow.class);

    private final ChecklistService checklistService;

    public OnboardingWorkflow(ChecklistService checklistService) {
        this.checklistService = checklistService;
    }

    public void start(Employee employee) {
        log.info("Onboarding workflow triggered for new hire {} ({} {})",
                employee.getEmployeeNo(), employee.getFirstName(), employee.getLastName());
        try {
            var templates = checklistService.templatesByFlow(ChecklistFlowType.ONBOARDING);
            for (var tpl : templates) {
                checklistService.start(new StartAssignmentRequest(
                        tpl.id(), employee.getId(),
                        employee.getHireDate(),
                        "Auto-started on employee creation"));
                log.info("Onboarding checklist '{}' started for employee {}",
                        tpl.name(), employee.getEmployeeNo());
            }
        } catch (Exception ex) {
            log.error("Failed to auto-start onboarding checklist for employee {}: {}",
                    employee.getEmployeeNo(), ex.getMessage(), ex);
        }
    }
}
