-- M79 / Phase 2 — Personal-info change approval workflow (P2-25/26)
-- and supporting infrastructure for validation hardening (P2-23/24).
--
-- When a non-HR employee edits their own personal fields (email, phone,
-- emergency contact, address), the SPA routes the change through this
-- table instead of applying it directly. HR approves / rejects, and the
-- linked workflow drives the state machine + audit trail.
--
-- Stored as a generic key/value diff so we don't have to alter the
-- request shape every time a new field becomes self-editable.

CREATE TABLE IF NOT EXISTS core_hr.personal_info_change_request (
    id                     UUID         PRIMARY KEY,
    request_no             VARCHAR(20)  NOT NULL UNIQUE,
    employee_id            UUID         NOT NULL REFERENCES core_hr.employee (id) ON DELETE CASCADE,
    -- Field key (e.g. "email", "phone", "addressLine1") — the service
    -- routes each known key to the right setter on the target entity.
    field_key              VARCHAR(80)  NOT NULL,
    old_value              TEXT,
    new_value              TEXT,
    reason                 TEXT,
    status                 VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    workflow_instance_id   UUID,
    submitted_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    submitted_by           VARCHAR(80),
    decided_at             TIMESTAMPTZ,
    decided_by             VARCHAR(80),
    decision_comment       TEXT,
    applied_at             TIMESTAMPTZ,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by             VARCHAR(80),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by             VARCHAR(80),
    CONSTRAINT chk_picr_status CHECK (status IN (
        'PENDING','APPROVED','REJECTED','APPLIED','CANCELLED'
    )),
    CONSTRAINT chk_picr_field CHECK (field_key IN (
        'email','phone','addressLine1','addressLine2','city','district',
        'postalCode','country','maritalStatus','emergencyContactName',
        'emergencyContactPhone'
    ))
);

CREATE SEQUENCE IF NOT EXISTS core_hr.personal_info_no_seq START WITH 1;

CREATE INDEX IF NOT EXISTS idx_picr_employee
    ON core_hr.personal_info_change_request (employee_id, submitted_at DESC);

CREATE INDEX IF NOT EXISTS idx_picr_status
    ON core_hr.personal_info_change_request (status);


-- ── Workflow definition seed ───────────────────────────────────────────────
-- Mirrors the M67 disciplinary shape (HR_SPECIALIST → HR_ADMIN). Auto-approve
-- is FALSE so every personal-info edit gets a proper audit trail.

INSERT INTO workflow.workflow_definition (id, code, name, description, auto_approve, active)
VALUES (
    '7777aaaa-bbbb-cccc-dddd-eeeeffff0001',
    'PERSONAL_INFO_CHANGE_APPROVAL',
    'Personal info change approval',
    'M79 / P2-25/26. HR specialist review then HR admin sign-off for employee-initiated personal-info edits.',
    FALSE,
    TRUE
) ON CONFLICT (id) DO NOTHING;

INSERT INTO workflow.workflow_step (definition_id, step_order, name, approver_role)
VALUES
    ('7777aaaa-bbbb-cccc-dddd-eeeeffff0001', 1, 'HR specialist review', 'ROLE_HR_SPECIALIST'),
    ('7777aaaa-bbbb-cccc-dddd-eeeeffff0001', 2, 'HR admin sign-off',    'ROLE_HR_ADMIN')
ON CONFLICT DO NOTHING;
