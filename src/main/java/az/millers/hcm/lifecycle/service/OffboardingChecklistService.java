package az.millers.hcm.lifecycle.service;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.lifecycle.domain.ChecklistFlowType;
import az.millers.hcm.lifecycle.event.OffboardingCaseActivatedEvent;
import az.millers.hcm.lifecycle.repo.OffboardingCaseRepository;

@Service
public class OffboardingChecklistService {

    private static final Logger log = LoggerFactory.getLogger(OffboardingChecklistService.class);

    private final ChecklistService checklists;
    private final OffboardingCaseRepository cases;

    public OffboardingChecklistService(ChecklistService checklists, OffboardingCaseRepository cases) {
        this.checklists = checklists;
        this.cases = cases;
    }

    /**
     * Auto-starts a matched OFFBOARDING checklist when a case becomes IN_PROGRESS.
     * Stores the assignment ID back on the case for direct lookup.
     * Non-fatal — a missing template or already-active assignment is logged and skipped.
     */
    @EventListener
    @Transactional
    public void onCaseActivated(OffboardingCaseActivatedEvent event) {
        try {
            // Use last working date as anchor (task due-date baseline); fall back to today.
            LocalDate anchor = cases.findById(event.caseId())
                    .map(c -> c.getLastWorkingDate() != null ? c.getLastWorkingDate() : LocalDate.now())
                    .orElse(LocalDate.now());

            checklists.startMatched(event.employeeId(), ChecklistFlowType.OFFBOARDING,
                            anchor, "Exit checklist for offboarding case")
                    .ifPresentOrElse(
                            assignment -> {
                                cases.findById(event.caseId()).ifPresent(c -> {
                                    c.setChecklistAssignmentId(assignment.id());
                                    cases.save(c);
                                });
                                log.info("Exit checklist {} started for offboarding case {}",
                                        assignment.id(), event.caseId());
                            },
                            () -> log.warn("No OFFBOARDING template matched for employee {} — exit checklist not started",
                                    event.employeeId())
                    );
        } catch (Exception e) {
            log.warn("Failed to start exit checklist for case {}: {}", event.caseId(), e.getMessage());
        }
    }
}
