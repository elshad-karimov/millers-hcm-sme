package az.millers.hcm.workflow.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import az.millers.hcm.notifications.NotificationService;
import az.millers.hcm.notifications.domain.NotificationCategory;
import az.millers.hcm.workflow.domain.WorkflowStatus;
import az.millers.hcm.workflow.event.WorkflowCompletedEvent;

/**
 * Generic listener that notifies the workflow submitter of approval outcomes
 * (M188 / PRD WORKFLOW_OUTCOME category).
 *
 * <p>Fires for every terminal {@link WorkflowCompletedEvent} across all
 * workflow types. The submitter (identified by {@code initiatedBy}) receives:
 * <ul>
 *   <li>APPROVED / AUTO_APPROVED — a success notification.</li>
 *   <li>REJECTED / RETURNED — a rejection notification with the approver comment.</li>
 *   <li>CANCELLED — no notification (initiator cancelled it themselves).</li>
 *   <li>Other terminal states — no notification.</li>
 * </ul>
 *
 * <p>Skips if {@code initiatedBy} is blank (programmatic or system-initiated
 * workflows). Uses the mutable {@link NotificationCategory#WORKFLOW_OUTCOME}
 * category so users can opt out per channel.
 *
 * <p>{@code @Async} ensures notification failures never roll back the
 * upstream approval transaction.
 */
@Component
public class WorkflowOutcomeNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(WorkflowOutcomeNotificationListener.class);

    private final NotificationService notifications;

    public WorkflowOutcomeNotificationListener(NotificationService notifications) {
        this.notifications = notifications;
    }

    @Async
    @EventListener
    public void onWorkflowCompleted(WorkflowCompletedEvent event) {
        String submitter = event.initiatedBy();
        if (submitter == null || submitter.isBlank()) return;

        // Don't notify the submitter for cancellations — they cancelled it themselves.
        if (event.status() == WorkflowStatus.CANCELLED) return;

        String title;
        String body;

        switch (event.status()) {
            case APPROVED, AUTO_APPROVED -> {
                title = "Request approved: " + friendlyName(event.definitionCode());
                body  = "Your " + friendlyName(event.definitionCode())
                        + " request has been approved."
                        + (blank(event.comment()) ? "" : " Comment: " + event.comment());
            }
            case REJECTED -> {
                title = "Request rejected: " + friendlyName(event.definitionCode());
                body  = "Your " + friendlyName(event.definitionCode())
                        + " request was rejected."
                        + (blank(event.comment()) ? "" : " Reason: " + event.comment());
            }
            case RETURNED -> {
                title = "Request returned for revision: " + friendlyName(event.definitionCode());
                body  = "Your " + friendlyName(event.definitionCode())
                        + " request was returned for revision."
                        + (blank(event.comment()) ? "" : " Comment: " + event.comment());
            }
            default -> {
                return;   // EXPIRED, WITHDRAWN, etc. — no notification
            }
        }

        try {
            notifications.notifyAll(
                    NotificationCategory.WORKFLOW_OUTCOME,
                    submitter, title, body,
                    event.subjectModule(), event.subjectEntity(), event.subjectId());
        } catch (Exception ex) {
            log.warn("WorkflowOutcomeNotificationListener: failed to notify {} for {} {}: {}",
                    submitter, event.definitionCode(), event.instanceId(), ex.getMessage());
        }
    }

    /** Converts a workflow definition code to a human-readable label. */
    private static String friendlyName(String code) {
        if (code == null) return "workflow";
        return switch (code) {
            case "LEAVE_REQUEST_APPROVAL"        -> "leave";
            case "BUSINESS_TRIP_APPROVAL"        -> "business trip";
            case "TERMINATION_APPROVAL"          -> "termination";
            case "CONTRACT_CHANGE_APPROVAL"      -> "contract change";
            case "PERFORMANCE_REVIEW_APPROVAL"   -> "performance review";
            case "PERMISSION_APPROVAL"           -> "special permission";
            case "TIMESHEET_APPROVAL"            -> "timesheet";
            case "LETTER_REQUEST_APPROVAL"       -> "letter";
            case "ORG_STRUCTURE_APPROVAL"        -> "org structure change";
            case "DISCIPLINARY_ACTION_APPROVAL"  -> "disciplinary action";
            case "HEADCOUNT_CHANGE_REQUEST"      -> "headcount change";
            case "PERSONAL_INFO_CHANGE_APPROVAL" -> "personal info change";
            case "PAYROLL_APPROVAL"              -> "payroll run";
            case "BONUS_RUN_APPROVAL"            -> "bonus run";
            default                              -> code.toLowerCase().replace('_', ' ');
        };
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
