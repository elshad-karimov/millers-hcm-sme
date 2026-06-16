-- ----------------------------------------------------------------------------
-- M304 — Onboarding PRD Phase B.4: document / contract / policy-ack tasks.
--
-- Two halves:
--  • Document/contract files reuse the M16 attachment registry directly
--    (owner_module='lifecycle', owner_entity='checklisttaskstatus',
--    owner_id=<task status id>) — no schema needed here.
--  • Policy acknowledgement / contract signing leave a compliance audit row:
--    who acknowledged what, when, with what statement. That is this table.
--
-- Acknowledging a task marks the originating checklist task DONE.
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS lifecycle.onboarding_acknowledgement (
    id              uuid PRIMARY KEY,
    employee_id     uuid         NOT NULL,
    assignment_id   uuid         REFERENCES lifecycle.checklist_assignment(id) ON DELETE CASCADE,
    -- One acknowledgement per checklist task.
    task_status_id  uuid         NOT NULL UNIQUE REFERENCES lifecycle.checklist_task_status(id) ON DELETE CASCADE,
    kind            varchar(16)  NOT NULL,   -- POLICY | CONTRACT | DOCUMENT
    statement       text,                    -- "I have read and agree to the Code of Conduct"
    reference       varchar(300),            -- policy code / document name / contract ref
    acknowledged_by varchar(80),
    acknowledged_at timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT onboarding_acknowledgement_kind_check
        CHECK (kind IN ('POLICY','CONTRACT','DOCUMENT'))
);

CREATE INDEX IF NOT EXISTS onboarding_acknowledgement_employee_idx
    ON lifecycle.onboarding_acknowledgement (employee_id);

COMMENT ON TABLE lifecycle.onboarding_acknowledgement IS
    'M304 — onboarding policy-acknowledgement / contract-signing compliance audit row. Marks the checklist task DONE on acknowledge.';
