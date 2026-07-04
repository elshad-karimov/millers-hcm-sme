-- Lifecycle module (PRD Sections 8.11 Termination, 8.12 Contract Change).
-- Termination: workflow-driven exit flow that flips Employee.status to TERMINATED,
-- frees the staffing position, and computes an unused-leave payout snapshot.
-- Contract Change: workflow-driven amendments — salary, position, manager,
-- department, grade, location, job title — applied on approval.

CREATE SEQUENCE lifecycle.termination_no_seq      START WITH 1;
CREATE SEQUENCE lifecycle.contract_change_no_seq  START WITH 1;

CREATE TABLE lifecycle.termination_request (
    id                       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    termination_no           VARCHAR(32)  NOT NULL UNIQUE,
    employee_id              UUID         NOT NULL,

    -- VOLUNTARY_RESIGNATION, INVOLUNTARY_DISMISSAL, MUTUAL_AGREEMENT,
    -- END_OF_CONTRACT, RETIREMENT, REDUNDANCY, PROBATION_FAIL, DEATH, OTHER (PRD 8.11.1)
    reason_code              VARCHAR(40)  NOT NULL,
    reason_detail            TEXT,

    notice_date              DATE         NOT NULL,           -- when employee notified / notice received
    last_working_date        DATE         NOT NULL,
    effective_date           DATE         NOT NULL,           -- contract end date

    -- DRAFT, PENDING, APPROVED, REJECTED, CANCELLED, PROCESSED (PRD 8.11.2)
    status                   VARCHAR(32)  NOT NULL,
    workflow_instance_id     UUID,

    -- Payout snapshot computed on PROCESS (PRD 8.11.5):
    unused_leave_days        NUMERIC(9, 2),
    unused_leave_payout      NUMERIC(14, 2),
    severance_amount         NUMERIC(14, 2),
    final_settlement_amount  NUMERIC(14, 2),
    currency                 VARCHAR(3),
    payout_calc_details      JSONB,

    -- Clearance checklist (PRD 8.11.4) — simple booleans for now
    clearance_it             BOOLEAN      NOT NULL DEFAULT FALSE,
    clearance_hr             BOOLEAN      NOT NULL DEFAULT FALSE,
    clearance_finance        BOOLEAN      NOT NULL DEFAULT FALSE,
    clearance_assets         BOOLEAN      NOT NULL DEFAULT FALSE,

    attachment_urls          TEXT,
    note                     TEXT,

    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by               VARCHAR(120),
    processed_at             TIMESTAMPTZ,
    processed_by             VARCHAR(120),

    CONSTRAINT chk_term_dates CHECK (last_working_date >= notice_date
                                     AND effective_date >= last_working_date)
);

CREATE INDEX idx_term_employee ON lifecycle.termination_request (employee_id);
CREATE INDEX idx_term_status   ON lifecycle.termination_request (status);

-- Optional exit-interview record attached to a termination (PRD 8.11.3).
CREATE TABLE lifecycle.exit_interview (
    id                       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    termination_id           UUID         NOT NULL UNIQUE REFERENCES lifecycle.termination_request (id) ON DELETE CASCADE,
    conducted_at             TIMESTAMPTZ,
    conducted_by             VARCHAR(120),
    overall_rating           INTEGER,     -- 1..5
    would_recommend          BOOLEAN,
    reason_for_leaving       TEXT,
    feedback                 TEXT,
    improvement_suggestions  TEXT,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_exit_rating CHECK (overall_rating IS NULL OR overall_rating BETWEEN 1 AND 5)
);

-- Contract Change (PRD 8.12). Single table covers all change types; the
-- old/new values stored as JSON for auditability and type-agnostic UI.
CREATE TABLE lifecycle.contract_change (
    id                       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    change_no                VARCHAR(32)  NOT NULL UNIQUE,
    employee_id              UUID         NOT NULL,

    -- SALARY, POSITION, DEPARTMENT, MANAGER, GRADE, LOCATION, JOB_TITLE,
    -- EMPLOYMENT_TYPE, COST_CENTRE  (PRD 8.12.1)
    change_type              VARCHAR(40)  NOT NULL,

    effective_date           DATE         NOT NULL,
    reason                   TEXT,

    old_value                JSONB,                            -- snapshot at submit
    new_value                JSONB        NOT NULL,            -- payload to apply

    -- DRAFT, PENDING, APPROVED, REJECTED, CANCELLED, APPLIED (PRD 8.12.2)
    status                   VARCHAR(32)  NOT NULL,
    workflow_instance_id     UUID,

    applied_at               TIMESTAMPTZ,
    applied_by               VARCHAR(120),
    attachment_urls          TEXT,
    note                     TEXT,

    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by               VARCHAR(120)
);

CREATE INDEX idx_cc_employee ON lifecycle.contract_change (employee_id);
CREATE INDEX idx_cc_status   ON lifecycle.contract_change (status);
CREATE INDEX idx_cc_type     ON lifecycle.contract_change (change_type);

-- ---------- Workflow definitions ----------

-- TERMINATION_APPROVAL (PRD 8.11.2): Manager → HR/Legal → Executive.
INSERT INTO workflow.workflow_definition (id, code, name, description, auto_approve, active)
VALUES (
    '88888888-8888-8888-8888-888888888888',
    'TERMINATION_APPROVAL',
    'Termination Approval',
    'Multi-step approval for terminations (PRD 8.11.2). Manager / HR / Legal / Executive roles approximated until those roles are wired to users.',
    FALSE,
    TRUE
);
INSERT INTO workflow.workflow_step (definition_id, step_order, name, approver_role)
VALUES
    ('88888888-8888-8888-8888-888888888888', 1, 'Manager review',     'ROLE_HR_SPECIALIST'),
    ('88888888-8888-8888-8888-888888888888', 2, 'HR / Legal review',  'ROLE_HR_ADMIN'),
    ('88888888-8888-8888-8888-888888888888', 3, 'Executive sign-off', 'ROLE_SYSTEM_ADMIN');

-- CONTRACT_CHANGE_APPROVAL (PRD 8.12.2): Manager → HR → Finance/Executive.
INSERT INTO workflow.workflow_definition (id, code, name, description, auto_approve, active)
VALUES (
    '99999999-9999-9999-9999-999999999999',
    'CONTRACT_CHANGE_APPROVAL',
    'Contract Change Approval',
    'Multi-step approval for contract amendments (PRD 8.12.2). Manager / HR / Finance roles approximated until those roles are wired to users.',
    FALSE,
    TRUE
);
INSERT INTO workflow.workflow_step (definition_id, step_order, name, approver_role)
VALUES
    ('99999999-9999-9999-9999-999999999999', 1, 'Manager review',     'ROLE_HR_SPECIALIST'),
    ('99999999-9999-9999-9999-999999999999', 2, 'HR review',          'ROLE_HR_ADMIN'),
    ('99999999-9999-9999-9999-999999999999', 3, 'Finance / Executive','ROLE_SYSTEM_ADMIN');
