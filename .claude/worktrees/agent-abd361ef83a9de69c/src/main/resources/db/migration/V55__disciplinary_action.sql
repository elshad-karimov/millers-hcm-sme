-- M67 / Phase 1 — Disciplinary actions (P1-12, CRITICAL — legal compliance)
--
-- Every progressive-discipline event the company records, from a verbal
-- warning to a dismissal recommendation. Routes through an approval workflow
-- (DISCIPLINARY_ACTION_APPROVAL, seeded below) — same pattern as
-- CONTRACT_CHANGE_APPROVAL, so the WorkflowService.start() call and the
-- LifecycleWorkflowListener wiring all work without any service-engine
-- changes.
--
-- The status flow is intentionally narrow:
--   DRAFT     — initial create (HR-only visibility)
--   PENDING   — workflow in flight
--   APPROVED  — workflow approved (ready to issue)
--   ISSUED    — action delivered to the employee
--   APPEALED  — employee filed an appeal
--   CLOSED    — final terminal state (success / withdrawal / appeal resolved)
--   REJECTED  — workflow rejected, no further action

CREATE SEQUENCE IF NOT EXISTS lifecycle.disciplinary_no_seq START 1;

CREATE TABLE IF NOT EXISTS lifecycle.disciplinary_action (
    id                      UUID         PRIMARY KEY,
    action_no               VARCHAR(20)  NOT NULL UNIQUE,
    employee_id             UUID         NOT NULL REFERENCES core_hr.employee (id) ON DELETE CASCADE,
    action_type             VARCHAR(20)  NOT NULL,
    incident_date           DATE         NOT NULL,
    action_date             DATE         NOT NULL,
    issued_by               VARCHAR(80),
    description             TEXT,
    status                  VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',

    -- Workflow integration (PRD 8.12.2 pattern) — populated when the
    -- approval workflow starts. Lets the API expose "pending approvers" via
    -- the standard /api/workflow inbox.
    workflow_instance_id    UUID,

    -- Soft FK to a parent disciplinary record so progressive discipline can
    -- be threaded (a Verbal Warning escalates to a Written Warning escalates
    -- to a Final Warning). Same employee + same kind of conduct chain.
    linked_case_id          UUID,

    -- Appeal tracking (PRD compliance — employees may appeal disciplinary
    -- decisions within statutory notice periods).
    appeal_flag             BOOLEAN      NOT NULL DEFAULT FALSE,
    appeal_reason           TEXT,
    appeal_outcome          TEXT,
    appealed_at             TIMESTAMPTZ,

    issued_at               TIMESTAMPTZ,
    closed_at               TIMESTAMPTZ,

    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by              VARCHAR(80),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by              VARCHAR(80),

    CONSTRAINT chk_disc_action_type CHECK (action_type IN (
        'VERBAL_WARNING','WRITTEN_WARNING','FINAL_WARNING',
        'PENALTY','SUSPENSION','INVESTIGATION','DISMISSAL'
    )),
    CONSTRAINT chk_disc_status CHECK (status IN (
        'DRAFT','PENDING','APPROVED','ISSUED','APPEALED','CLOSED','REJECTED'
    )),
    CONSTRAINT chk_disc_dates CHECK (action_date >= incident_date)
);

CREATE INDEX IF NOT EXISTS idx_disc_employee
    ON lifecycle.disciplinary_action (employee_id, action_date DESC);

CREATE INDEX IF NOT EXISTS idx_disc_status
    ON lifecycle.disciplinary_action (status);


-- ── Workflow definition seed ──────────────────────────────────────────────
-- Reuses the same shape as CONTRACT_CHANGE_APPROVAL (V18). The auto-approve
-- flag is FALSE — every disciplinary action requires explicit HR sign-off,
-- with an extra step for DISMISSAL-level severity (handled in service code
-- by gating the workflow start on action_type when needed).

INSERT INTO workflow.workflow_definition (id, code, name, description, auto_approve, active)
VALUES (
    'd1501501-d150-d150-d150-d15015015015',
    'DISCIPLINARY_ACTION_APPROVAL',
    'Disciplinary action approval',
    'M67 / P1-12. Two-step approval — HR Specialist review then HR Admin sign-off. Dismissals additionally require Legal / System Admin sign-off.',
    FALSE,
    TRUE
) ON CONFLICT (id) DO NOTHING;

INSERT INTO workflow.workflow_step (definition_id, step_order, name, approver_role)
VALUES
    ('d1501501-d150-d150-d150-d15015015015', 1, 'HR specialist review', 'ROLE_HR_SPECIALIST'),
    ('d1501501-d150-d150-d150-d15015015015', 2, 'HR admin sign-off',    'ROLE_HR_ADMIN'),
    ('d1501501-d150-d150-d150-d15015015015', 3, 'Legal / Admin (dismissals only)', 'ROLE_SYSTEM_ADMIN')
ON CONFLICT DO NOTHING;
