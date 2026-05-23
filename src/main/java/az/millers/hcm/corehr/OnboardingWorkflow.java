package az.millers.hcm.corehr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import az.millers.hcm.corehr.domain.Employee;

/**
 * Stub seam for the onboarding workflow fired on new-hire creation (PRD 8.1.4).
 *
 * <p>The configurable Workflow Engine (PRD Section 9) is a later module; this
 * placeholder records intent so the Core HR acceptance criterion is satisfied
 * end-to-end without coupling to an engine that does not exist yet.
 */
@Component
public class OnboardingWorkflow {

    private static final Logger log = LoggerFactory.getLogger(OnboardingWorkflow.class);

    public void start(Employee employee) {
        log.info("Onboarding workflow triggered for new hire {} ({} {})",
                employee.getEmployeeNo(), employee.getFirstName(), employee.getLastName());
    }
}
