-- M172 (PRD §9.1): Parallel approval and conditional step routing.
--
-- Parallel: multiple WorkflowStep rows at the same step_order with
-- parallel=TRUE form a "gate". All must be voted APPROVE before the
-- instance advances. Votes are tracked in workflow_parallel_vote.
--
-- Conditional: when condition_spel is set on a step, the SpEL expression
-- is evaluated against the instance's payload JSON. If it returns false
-- the step is skipped automatically.
--
-- workflow_instance gains current_step_roles TEXT[] — the set of approver
-- roles still outstanding in the current parallel gate. Non-null only during
-- a parallel gate; sequential steps keep using current_step_role (VARCHAR).

ALTER TABLE workflow.workflow_step
    ADD COLUMN IF NOT EXISTS parallel       BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS condition_spel VARCHAR(500);

ALTER TABLE workflow.workflow_instance
    ADD COLUMN IF NOT EXISTS current_step_roles TEXT[];

COMMENT ON COLUMN workflow.workflow_step.parallel
    IS 'TRUE = step is part of a parallel gate at this step_order. All parallel steps at the same step_order must be approved before advancing.';
COMMENT ON COLUMN workflow.workflow_step.condition_spel
    IS 'Optional SpEL expression evaluated against instance.payload JSON. Step is skipped when the expression returns false.';
COMMENT ON COLUMN workflow.workflow_instance.current_step_roles
    IS 'Non-null during a parallel gate: set of approver_role values still pending a vote. Null for sequential steps.';

-- Parallel vote log.
CREATE TABLE IF NOT EXISTS workflow.workflow_parallel_vote (
    id          UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    instance_id UUID         NOT NULL REFERENCES workflow.workflow_instance(id),
    step_id     UUID         NOT NULL REFERENCES workflow.workflow_step(id),
    step_order  INTEGER      NOT NULL,
    voter       VARCHAR(255) NOT NULL,
    voted_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    approved    BOOLEAN      NOT NULL,
    comment     TEXT,
    -- One vote per (instance, step) — a voter can cast exactly one vote per parallel step.
    UNIQUE (instance_id, step_id)
);

CREATE INDEX IF NOT EXISTS idx_parallel_vote_instance_step
    ON workflow.workflow_parallel_vote(instance_id, step_order);

COMMENT ON TABLE workflow.workflow_parallel_vote
    IS 'Records individual votes on parallel-gate workflow steps (M172 / PRD §9.1).';
