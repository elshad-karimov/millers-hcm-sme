-- M126 — Workflow SLA + breach detection.
--
-- M14 already shipped the workflow_step.sla_hours column, but the
-- engine never acted on it. This migration adds:
--   1. escalation_action + escalation_to on workflow_step so an SLA
--      breach knows what to do (NOTIFY in Phase 1; future:
--      REASSIGN, AUTO_APPROVE, AUTO_REJECT).
--   2. current_step_entered_at on workflow_instance so the scheduler
--      can compute "how long has this been at the current step".
--   3. workflow.sla_breach — append-only log so the same breach
--      doesn't get notified twice and the HR dashboard can show
--      historical breaches.

ALTER TABLE workflow.workflow_step
    ADD COLUMN escalation_action VARCHAR(32) NOT NULL DEFAULT 'NOTIFY',
    ADD COLUMN escalation_to     VARCHAR(160);

ALTER TABLE workflow.workflow_step
    ADD CONSTRAINT chk_workflow_step_escalation
        CHECK (escalation_action IN ('NOTIFY', 'REASSIGN', 'AUTO_APPROVE', 'AUTO_REJECT'));

ALTER TABLE workflow.workflow_instance
    ADD COLUMN current_step_entered_at TIMESTAMPTZ;

-- Backfill: any open instance that doesn't have an entered_at yet uses
-- initiated_at. The scheduler will see this on its next run and treat
-- the instance as having been at the current step since the start.
UPDATE workflow.workflow_instance
   SET current_step_entered_at = initiated_at
 WHERE current_step_entered_at IS NULL;

CREATE INDEX ix_workflow_instance_step_entered
    ON workflow.workflow_instance (current_step_entered_at)
    WHERE status IN ('PENDING');

CREATE TABLE workflow.sla_breach (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    instance_id     UUID         NOT NULL
                        REFERENCES workflow.workflow_instance (id) ON DELETE CASCADE,
    step_index      INTEGER      NOT NULL,
    breached_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    /* Hours over SLA at detection time (informational; the SPA uses
     * this to colour the breach severity). */
    hours_overdue   NUMERIC(6,2) NOT NULL,
    action_taken    VARCHAR(32)  NOT NULL,
    /* Who got notified (escalation_to or fallback). */
    notified_target VARCHAR(160),
    /* Idempotency: at most one breach row per (instance, step_index)
     * so a long-running breach doesn't spam every 5 minutes. The
     * scheduler reads this index to decide whether to fire. */
    UNIQUE (instance_id, step_index)
);

CREATE INDEX ix_sla_breach_recent
    ON workflow.sla_breach (breached_at DESC);
