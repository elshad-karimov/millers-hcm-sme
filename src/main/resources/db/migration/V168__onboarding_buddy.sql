-- ----------------------------------------------------------------------------
-- M305 — Onboarding PRD Phase C.1: buddy / mentor assignment.
--
-- A new hire is paired with a buddy (peer) and/or a mentor (another employee)
-- for their onboarding. A BUDDY_TASK checklist task is closed when the buddy is
-- assigned. The buddy gains visibility of their mentees; the manager dashboard
-- (Phase E) reads "pending buddy assignments" from open BUDDY_TASK tasks.
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS lifecycle.onboarding_buddy (
    id                uuid PRIMARY KEY,
    employee_id       uuid         NOT NULL,   -- the new hire
    buddy_employee_id uuid         NOT NULL,   -- the buddy / mentor
    assignment_id     uuid         REFERENCES lifecycle.checklist_assignment(id) ON DELETE CASCADE,
    task_status_id    uuid         REFERENCES lifecycle.checklist_task_status(id) ON DELETE SET NULL,
    role              varchar(12)  NOT NULL DEFAULT 'BUDDY',   -- BUDDY | MENTOR
    status            varchar(12)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | ENDED | CANCELLED
    notes             text,
    assigned_by       varchar(80),
    assigned_at       timestamptz  NOT NULL DEFAULT now(),
    ended_at          timestamptz,
    CONSTRAINT onboarding_buddy_role_check   CHECK (role   IN ('BUDDY','MENTOR')),
    CONSTRAINT onboarding_buddy_status_check CHECK (status IN ('ACTIVE','ENDED','CANCELLED')),
    CONSTRAINT onboarding_buddy_not_self     CHECK (employee_id <> buddy_employee_id)
);

-- At most one active buddy + one active mentor per hire.
CREATE UNIQUE INDEX IF NOT EXISTS onboarding_buddy_active_unique
    ON lifecycle.onboarding_buddy (employee_id, role)
    WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS onboarding_buddy_employee_idx ON lifecycle.onboarding_buddy (employee_id);
CREATE INDEX IF NOT EXISTS onboarding_buddy_buddy_idx    ON lifecycle.onboarding_buddy (buddy_employee_id);

COMMENT ON TABLE lifecycle.onboarding_buddy IS
    'M305 — onboarding buddy/mentor pairing. Assigning closes the BUDDY_TASK; buddy sees their mentees.';
