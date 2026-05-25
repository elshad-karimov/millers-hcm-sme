package az.millers.hcm.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import az.millers.hcm.workflow.event.WorkflowCompletedEvent;
import az.millers.hcm.workflow.repo.WorkflowInstanceRepository;

/**
 * Listens for {@link WorkflowCompletedEvent} (terminal states only) and
 * notifies the request initiator via all available channels (IN_APP + EMAIL
 * + PUSH).
 *
 * <p>Uses {@link TransactionalEventListener} with
 * {@link TransactionPhase#AFTER_COMMIT} so the listener always runs after
 * the workflow transaction has committed — the workflow instance is therefore
 * guaranteed to be readable when this code executes.
 *
 * <p>{@link Async} moves execution to the Spring task executor thread pool so
 * notification delivery does not block the caller's HTTP thread.
 */
@Component
public class WorkflowNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(WorkflowNotificationListener.class);

    private final WorkflowInstanceRepository instances;
    private final NotificationService notificationService;

    public WorkflowNotificationListener(WorkflowInstanceRepository instances,
                                         NotificationService notificationService) {
        this.instances = instances;
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void onWorkflowCompleted(WorkflowCompletedEvent event) {
        // Fetch the instance in a fresh (read-only) context to get initiatedBy.
        instances.findById(event.instanceId()).ifPresentOrElse(instance -> {
            String initiator = instance.getInitiatedBy();
            String statusLabel = event.status().name();

            // "Leave Request APPROVED"
            String title = event.subjectEntity() + " " + statusLabel;

            // "Your Leave Request 'Annual Leave 3 days' has been APPROVED."
            StringBuilder bodyBuilder = new StringBuilder();
            bodyBuilder.append("Your ").append(event.subjectEntity())
                    .append(" '").append(instance.getTitle())
                    .append("' has been ").append(statusLabel).append(".");
            if (event.comment() != null && !event.comment().isBlank()) {
                bodyBuilder.append(" Comment: ").append(event.comment());
            }

            notificationService.notifyAll(
                    initiator,
                    title,
                    bodyBuilder.toString(),
                    event.subjectModule(),
                    event.subjectEntity(),
                    event.subjectId());

        }, () -> log.warn("WorkflowNotificationListener: instance {} not found after commit — skipping notification",
                event.instanceId()));
    }
}
