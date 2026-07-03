-- HCM_12 Performance Management — M392 (Phase B.1)
-- Goal approval workflow (PRD §6.2 Employee→Manager) + goal progress-update
-- audit trail (§6.4). Submission is per goal PLAN (all of an employee's DRAFT
-- goals in a cycle at once) and enforces §37.5: goal weights must total exactly
-- 100 at submit time. One GOAL_APPROVAL workflow instance covers the plan;
-- subject entity = 'GoalPlan', subject id = the employee id (so the engine's
-- resolves_to_manager routing finds the direct manager).

ALTER TABLE performance.goal
    ADD COLUMN approval_status      varchar(20) NOT NULL DEFAULT 'NOT_SUBMITTED',
    ADD COLUMN workflow_instance_id uuid;

COMMENT ON COLUMN performance.goal.approval_status IS
    'HCM_12 M392 — NOT_SUBMITTED | PENDING_APPROVAL | APPROVED | REJECTED (plan-level workflow).';

CREATE INDEX goal_workflow_idx ON performance.goal (workflow_instance_id)
    WHERE workflow_instance_id IS NOT NULL;

-- §6.4 — every progress update is a traceable row (who, when, old → new).
CREATE TABLE performance.goal_progress_update (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    varchar(64) NOT NULL DEFAULT 'default',
    goal_id      uuid NOT NULL REFERENCES performance.goal (id) ON DELETE CASCADE,
    old_progress numeric(5,2),
    new_progress numeric(5,2) NOT NULL,
    old_status   varchar(24),
    new_status   varchar(24),
    note         varchar(1000),
    recorded_by  varchar(80),
    recorded_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX goal_progress_update_goal_idx ON performance.goal_progress_update (goal_id);

COMMENT ON TABLE performance.goal_progress_update IS
    'HCM_12 M392 — progress-update audit trail per goal (PRD §6.4).';

-- GOAL_APPROVAL workflow: single manager step (Employee→Manager per gap-check
-- default), 72h SLA with notify escalation.
INSERT INTO workflow.workflow_definition
    (id, code, name, description, auto_approve, active, created_at, updated_at)
VALUES (
    'ac120392-0000-0000-0000-000000000001',
    'GOAL_APPROVAL',
    'Goal Plan Approval',
    'Manager approval of an employee''s goal plan for a review cycle (HCM_12 §6.2; weights must total 100 — §37.5).',
    false, true, now(), now()
);

INSERT INTO workflow.workflow_step
    (id, definition_id, step_order, name, approver_role,
     resolves_to_manager, resolves_to_hrbp, sla_hours,
     escalation_action, escalation_to, parallel, condition_spel)
VALUES (
    'ac120392-0000-0000-0001-000000000001',
    'ac120392-0000-0000-0000-000000000001',
    0,
    'Manager approval',
    'ROLE_DEPARTMENT_MANAGER',
    true, false, 72,
    'NOTIFY', null, false, null
);
