-- V117 — Workflow substitute approver (M176 / PRD §9.1)
--
-- When the holder of a role is absent (e.g., on leave), an HR admin can
-- pre-configure a substitute role that may act on all workflow steps assigned
-- to the principal role during the defined date window.
--
-- This is distinct from delegation (M162 / workflow_instance.delegated_to):
--   • Delegation   — approver hand-off of a *single* instance to a named user.
--   • Substitution — HR-managed, role-level coverage for an *absence window*,
--                    automatically applies to all matching PENDING instances.

CREATE TABLE IF NOT EXISTS workflow.substitute_approver (
    id               UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    principal_role   VARCHAR(100) NOT NULL,   -- the absent role
    substitute_role  VARCHAR(100) NOT NULL,   -- the covering role
    start_date       DATE         NOT NULL,
    end_date         DATE         NOT NULL,
    reason           TEXT,
    created_by       VARCHAR(120) NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT substitute_approver_dates_check CHECK (end_date >= start_date),
    CONSTRAINT substitute_approver_no_self_sub  CHECK (principal_role <> substitute_role)
);

CREATE INDEX idx_sub_approver_lookup ON workflow.substitute_approver
    (substitute_role, start_date, end_date);

COMMENT ON TABLE workflow.substitute_approver IS
    'M176 — Role-level absence cover: while start_date ≤ today ≤ end_date
     a holder of substitute_role may act on any PENDING workflow step whose
     current_step_role = principal_role.';
