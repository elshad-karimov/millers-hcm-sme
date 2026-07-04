-- M77 / Phase 2 — HR letter template engine + self-service letter requests
-- (P2-16, P2-17).
--
-- Two tables in a new `hr_letters` schema, one workflow definition, and
-- two sequences for human-readable codes. No FK to attachment.attachment
-- (soft pointer) because attachments are written before this row when a
-- letter is rendered — keeping the FK soft sidesteps an ordering cycle.

CREATE SCHEMA IF NOT EXISTS hr_letters;

CREATE SEQUENCE IF NOT EXISTS hr_letters.template_no_seq START WITH 1;
CREATE SEQUENCE IF NOT EXISTS hr_letters.request_no_seq  START WITH 1;


-- ── hr_letters.letter_template ─────────────────────────────────────────────
-- Reusable template — body holds the rendered text with {{placeholder}}
-- markers. The placeholders_json column documents which placeholders the
-- service must populate (drives both the form UX and a validation pass at
-- render time).

CREATE TABLE IF NOT EXISTS hr_letters.letter_template (
    id                   UUID         PRIMARY KEY,
    code                 VARCHAR(40)  NOT NULL UNIQUE,
    name                 VARCHAR(200) NOT NULL,
    description          TEXT,
    -- Plain text or simple HTML — the service is content-agnostic; the
    -- frontend chooses the renderer based on `output_format`.
    body                 TEXT         NOT NULL,
    -- JSON map: { "purpose": "Reason for the letter", ... } — declarative
    -- field list driving the self-service form.
    placeholders_json    JSONB,
    output_format        VARCHAR(20)  NOT NULL DEFAULT 'TEXT',
    -- When true, every request against this template enters the
    -- LETTER_REQUEST_APPROVAL workflow. When false, requests
    -- auto-issue on submit (informational letters).
    requires_approval    BOOLEAN      NOT NULL DEFAULT TRUE,
    active               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by           VARCHAR(80),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by           VARCHAR(80),
    CONSTRAINT chk_letter_template_format CHECK (output_format IN ('TEXT','HTML'))
);

CREATE INDEX IF NOT EXISTS idx_letter_template_active
    ON hr_letters.letter_template (active);


-- ── hr_letters.letter_request ──────────────────────────────────────────────
-- One row per request. Status enum: DRAFT → PENDING → APPROVED → ISSUED,
-- or → REJECTED / CANCELLED at any point. ISSUED is set by the rendering
-- service after the body has been substituted and (optionally) persisted
-- as an attachment.

CREATE TABLE IF NOT EXISTS hr_letters.letter_request (
    id                     UUID         PRIMARY KEY,
    request_no             VARCHAR(20)  NOT NULL UNIQUE,
    template_id            UUID         NOT NULL REFERENCES hr_letters.letter_template (id) ON DELETE RESTRICT,
    employee_id            UUID         NOT NULL REFERENCES core_hr.employee (id) ON DELETE CASCADE,
    purpose                VARCHAR(500),
    -- Map of placeholder → value supplied by the requester. Merged with
    -- the auto-derived employee context at render time.
    custom_fields_json     JSONB,
    status                 VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    workflow_instance_id   UUID,
    rendered_body          TEXT,
    -- Soft pointer to attachment.attachment.id (the rendered PDF). NULL
    -- when output_format='TEXT' or rendering hasn't happened yet.
    attachment_id          UUID,
    requested_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    issued_at              TIMESTAMPTZ,
    decided_by             VARCHAR(80),
    decision_comment       TEXT,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by             VARCHAR(80),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by             VARCHAR(80),
    CONSTRAINT chk_letter_request_status CHECK (status IN (
        'DRAFT','PENDING','APPROVED','ISSUED','REJECTED','CANCELLED'
    ))
);

CREATE INDEX IF NOT EXISTS idx_letter_request_employee
    ON hr_letters.letter_request (employee_id, requested_at DESC);

CREATE INDEX IF NOT EXISTS idx_letter_request_status
    ON hr_letters.letter_request (status);


-- ── Workflow definition seed ───────────────────────────────────────────────
-- Mirrors the shape of CONTRACT_CHANGE_APPROVAL / DISCIPLINARY_ACTION_APPROVAL
-- (HR specialist + HR admin) but flagged auto_approve=FALSE so templates
-- that opt into approval get a proper paper trail.

INSERT INTO workflow.workflow_definition (id, code, name, description, auto_approve, active)
VALUES (
    '17717717-7777-7777-7777-777777777777',
    'LETTER_REQUEST_APPROVAL',
    'HR letter request approval',
    'M77 / P2-16/17. HR specialist review then HR admin sign-off. Auto-issue templates skip this workflow entirely.',
    FALSE,
    TRUE
) ON CONFLICT (id) DO NOTHING;

INSERT INTO workflow.workflow_step (definition_id, step_order, name, approver_role)
VALUES
    ('17717717-7777-7777-7777-777777777777', 1, 'HR specialist review', 'ROLE_HR_SPECIALIST'),
    ('17717717-7777-7777-7777-777777777777', 2, 'HR admin sign-off',    'ROLE_HR_ADMIN')
ON CONFLICT DO NOTHING;


-- ── Seed templates ────────────────────────────────────────────────────────
-- Three commonly-needed templates so the module is usable out-of-the-box.
-- Tenants can edit / disable these as needed.

INSERT INTO hr_letters.letter_template (id, code, name, description, body, placeholders_json,
                                         output_format, requires_approval, active, created_by)
VALUES (
    '00000000-0000-0000-0000-000000010001',
    'EMPLOYMENT_VERIFICATION',
    'Employment verification letter',
    'Certifies that the employee is currently employed.',
    'To Whom It May Concern,

This is to certify that {{employee.firstName}} {{employee.lastName}} (Employee No: {{employee.employeeNo}}) is currently employed with our company as {{employee.positionTitle}} in the {{employee.departmentName}} department since {{employee.hireDate}}.

Purpose of this letter: {{custom.purpose}}

Issued on {{today}}.

Sincerely,
HR Department',
    '{"purpose":"Reason this letter is being requested"}'::jsonb,
    'TEXT', TRUE, TRUE, 'system'
) ON CONFLICT (code) DO NOTHING;

INSERT INTO hr_letters.letter_template (id, code, name, description, body, placeholders_json,
                                         output_format, requires_approval, active, created_by)
VALUES (
    '00000000-0000-0000-0000-000000010002',
    'SALARY_CERTIFICATE',
    'Salary certificate',
    'Certifies the employee''s gross monthly salary — requires HR_ADMIN approval.',
    'To Whom It May Concern,

This is to certify that {{employee.firstName}} {{employee.lastName}} (Employee No: {{employee.employeeNo}}) is employed as {{employee.positionTitle}} since {{employee.hireDate}} and earns a gross monthly salary of {{custom.salaryDisplay}}.

Issued on {{today}}.

Sincerely,
HR Department',
    '{"salaryDisplay":"Salary including currency, e.g. 2,500 AZN"}'::jsonb,
    'TEXT', TRUE, TRUE, 'system'
) ON CONFLICT (code) DO NOTHING;

INSERT INTO hr_letters.letter_template (id, code, name, description, body, placeholders_json,
                                         output_format, requires_approval, active, created_by)
VALUES (
    '00000000-0000-0000-0000-000000010003',
    'NO_OBJECTION',
    'No-objection certificate',
    'Confirms there is no objection to the named external activity (e.g. travel, secondary work).',
    'To Whom It May Concern,

We confirm that the Company has no objection to {{employee.firstName}} {{employee.lastName}} (Employee No: {{employee.employeeNo}}) undertaking the following activity:

{{custom.activity}}

Issued on {{today}}.

Sincerely,
HR Department',
    '{"activity":"Activity being authorised"}'::jsonb,
    'TEXT', TRUE, TRUE, 'system'
) ON CONFLICT (code) DO NOTHING;
