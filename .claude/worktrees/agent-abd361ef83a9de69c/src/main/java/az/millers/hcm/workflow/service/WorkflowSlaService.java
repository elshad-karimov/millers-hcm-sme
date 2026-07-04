package az.millers.hcm.workflow.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import az.millers.hcm.notifications.NotificationService;
import az.millers.hcm.notifications.domain.NotificationCategory;
import az.millers.hcm.workflow.api.dto.SlaBreachResponse;
import az.millers.hcm.workflow.domain.EscalationAction;
import az.millers.hcm.workflow.domain.SlaBreach;
import az.millers.hcm.workflow.domain.WorkflowDefinition;
import az.millers.hcm.workflow.domain.WorkflowInstance;
import az.millers.hcm.workflow.domain.WorkflowStatus;
import az.millers.hcm.workflow.domain.WorkflowStep;
import az.millers.hcm.workflow.repo.SlaBreachRepository;
import az.millers.hcm.workflow.repo.WorkflowDefinitionRepository;
import az.millers.hcm.workflow.repo.WorkflowInstanceRepository;
import az.millers.hcm.workflow.repo.WorkflowStepRepository;

/**
 * M126 — orchestrates SLA-breach detection. Called from
 * {@link WorkflowSlaScheduler} on a cron, also surfaces read APIs
 * for the HR dashboard.
 *
 * <p>Idempotency: the DB unique constraint on
 * {@code (instance_id, step_index)} keeps the same step from being
 * recorded twice. A scheduler race that tries to insert both wins
 * with one success and one {@code DataIntegrityViolationException}
 * that's caught and discarded — Phase 1 keeps things simple.
 */
@Service
public class WorkflowSlaService {

    private final WorkflowInstanceRepository instances;
    private final WorkflowDefinitionRepository definitions;
    private final WorkflowStepRepository steps;
    private final SlaBreachRepository breaches;
    private final NotificationService notifications;

    public WorkflowSlaService(WorkflowInstanceRepository instances,
                              WorkflowDefinitionRepository definitions,
                              WorkflowStepRepository steps,
                              SlaBreachRepository breaches,
                              NotificationService notifications) {
        this.instances = instances;
        this.definitions = definitions;
        this.steps = steps;
        this.breaches = breaches;
        this.notifications = notifications;
    }

    /**
     * Sweep all open instances, detect new breaches, write log rows,
     * fire notifications. Returns the count of newly-recorded breaches
     * so the scheduler can log how busy this tick was.
     */
    @Transactional
    public int detectAndNotify() {
        OffsetDateTime now = OffsetDateTime.now();
        List<WorkflowInstance> open = instances.findByStatusOrderByInitiatedAtAsc(WorkflowStatus.PENDING);
        if (open.isEmpty()) return 0;

        // Pre-load every step we might need — typically a handful of
        // definitions, so one bulk fetch keeps this O(1) per instance.
        Map<UUID, List<WorkflowStep>> stepsByDef = new HashMap<>();
        for (WorkflowInstance i : open) {
            stepsByDef.computeIfAbsent(i.getDefinitionId(),
                    k -> steps.findByDefinitionIdOrderByStepOrderAsc(k));
        }

        int newCount = 0;
        for (WorkflowInstance i : open) {
            if (i.getCurrentStepEnteredAt() == null) continue;
            List<WorkflowStep> defSteps = stepsByDef.get(i.getDefinitionId());
            if (defSteps == null) continue;
            WorkflowStep step = findCurrentStep(defSteps, i.getCurrentStepIndex());
            if (step == null || step.getSlaHours() == null) continue;
            if (!SlaCalculator.isBreached(i.getCurrentStepEnteredAt(),
                    step.getSlaHours(), now)) continue;

            // Already recorded? (cheap idempotency probe before the
            // unique-constraint catch.)
            Optional<SlaBreach> existing = breaches.findByInstanceIdAndStepIndex(
                    i.getId(), i.getCurrentStepIndex());
            if (existing.isPresent()) continue;

            try {
                SlaBreach b = new SlaBreach();
                b.setInstanceId(i.getId());
                b.setStepIndex(i.getCurrentStepIndex());
                b.setBreachedAt(now);
                b.setHoursOverdue(SlaCalculator.hoursOverdue(
                        i.getCurrentStepEnteredAt(), step.getSlaHours(), now));
                b.setActionTaken(EscalationAction.NOTIFY);
                String target = pickTarget(step, i);
                b.setNotifiedTarget(target);
                breaches.save(b);
                newCount++;
                // Fire the notification. Use TRANSACTIONAL so it can't
                // be muted — an SLA breach is urgent by definition.
                if (target != null && !target.isBlank()) {
                    sendBreachNotice(target, i, step, b.getHoursOverdue());
                }
            } catch (DataIntegrityViolationException race) {
                // Another scheduler instance recorded the same breach
                // first. Fine — we just move on.
            }
        }
        return newCount;
    }

    // ── Read APIs for the HR dashboard ──────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SlaBreachResponse> listRecentBreaches(int limit) {
        int cap = Math.max(1, Math.min(limit, 500));
        List<SlaBreach> rows = breaches.findAllByOrderByBreachedAtDesc();
        if (rows.isEmpty()) return List.of();
        if (rows.size() > cap) rows = rows.subList(0, cap);
        // Enrich with instance data.
        Map<UUID, WorkflowInstance> byId = new HashMap<>();
        for (UUID id : rows.stream().map(SlaBreach::getInstanceId).distinct().toList()) {
            instances.findById(id).ifPresent(i -> byId.put(id, i));
        }
        List<SlaBreachResponse> out = new ArrayList<>(rows.size());
        for (SlaBreach b : rows) out.add(enrich(b, byId.get(b.getInstanceId())));
        return out;
    }

    @Transactional(readOnly = true)
    public List<SlaBreachResponse> historyFor(UUID instanceId) {
        List<SlaBreach> rows = breaches.findByInstanceIdOrderByBreachedAtDesc(instanceId);
        if (rows.isEmpty()) return List.of();
        WorkflowInstance i = instances.findById(instanceId).orElse(null);
        List<SlaBreachResponse> out = new ArrayList<>(rows.size());
        for (SlaBreach b : rows) out.add(enrich(b, i));
        return out;
    }

    // ── Internals ───────────────────────────────────────────────────────────

    private static WorkflowStep findCurrentStep(List<WorkflowStep> defSteps, int currentStepIndex) {
        for (WorkflowStep s : defSteps) {
            if (s.getStepOrder() == currentStepIndex) return s;
        }
        return null;
    }

    /**
     * Pick who to notify on breach. Order: escalation_to override → the
     * approver role of the next-higher step (a manager-step's
     * escalation target) → the step's own approver role as a fallback.
     */
    private String pickTarget(WorkflowStep step, WorkflowInstance instance) {
        if (step.getEscalationTo() != null && !step.getEscalationTo().isBlank()) {
            return step.getEscalationTo();
        }
        // No explicit target — fall back to the role itself. The
        // NotificationService treats this as "broadcast to anyone with
        // that role currently in the inbox" — Phase 2 can resolve a
        // specific user.
        return step.getApproverRole();
    }

    private void sendBreachNotice(String target, WorkflowInstance i,
                                   WorkflowStep step, BigDecimal hoursOverdue) {
        // Look up the definition name once so the title is friendlier
        // than just the code.
        String defName = definitions.findById(i.getDefinitionId())
                .map(WorkflowDefinition::getName).orElse(i.getDefinitionCode());
        String title = "SLA breach: " + defName;
        String body = String.format(
                "\"%s\" has been waiting %sh past its SLA at step %d (%s).",
                i.getTitle(),
                hoursOverdue.toPlainString(),
                step.getStepOrder(),
                step.getName());
        notifications.notifyAll(
                NotificationCategory.TRANSACTIONAL,
                target, title, body,
                "WORKFLOW", "WorkflowInstance", i.getId().toString());
    }

    private SlaBreachResponse enrich(SlaBreach b, WorkflowInstance i) {
        return new SlaBreachResponse(
                b.getId(), b.getInstanceId(), b.getStepIndex(),
                b.getBreachedAt(), b.getHoursOverdue(),
                b.getActionTaken(), b.getNotifiedTarget(),
                i == null ? null : i.getDefinitionCode(),
                i == null ? null : i.getSubjectModule(),
                i == null ? null : i.getSubjectEntity(),
                i == null ? null : i.getSubjectId(),
                i == null ? null : i.getTitle(),
                i == null ? null : i.getCurrentStepRole());
    }
}
