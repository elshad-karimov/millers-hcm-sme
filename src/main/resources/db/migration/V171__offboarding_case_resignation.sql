-- M313 — Offboarding Case foundation + employee resignation request (PRD §4, §8).
-- OffboardingCase is the orchestrating entity for all exit workflows;
-- ResignationRequest is the employee self-service entry point.

-- Sequences
CREATE SEQUENCE lifecycle.resignation_no_seq   START 1 INCREMENT 1;
CREATE SEQUENCE lifecycle.offboarding_case_no_seq START 1 INCREMENT 1;

-- Employee resignation request (PRD §4)
CREATE TABLE lifecycle.resignation_request (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    resignation_no              VARCHAR(20)  NOT NULL UNIQUE,
    employee_id                 UUID         NOT NULL REFERENCES core_hr.employee(id),
    resignation_date            DATE         NOT NULL,
    proposed_last_working_date  DATE         NOT NULL,
    calculated_last_working_date DATE,
    approved_last_working_date  DATE,
    notice_days_calculated      INT,
    reason_text                 TEXT,
    comments                    TEXT,
    status                      VARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    workflow_instance_id        UUID,
    created_by                  VARCHAR(100),
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT resignation_status_chk CHECK (status IN (
        'DRAFT','SUBMITTED','APPROVED','REJECTED','WITHDRAWN','CANCELLED'))
);

CREATE INDEX idx_resignation_employee ON lifecycle.resignation_request(employee_id);
CREATE INDEX idx_resignation_status   ON lifecycle.resignation_request(status);

-- Offboarding case — the orchestrator (PRD §8)
CREATE TABLE lifecycle.offboarding_case (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_no             VARCHAR(20)  NOT NULL UNIQUE,
    employee_id         UUID         NOT NULL REFERENCES core_hr.employee(id),
    source              VARCHAR(30)  NOT NULL,
    resignation_id      UUID         REFERENCES lifecycle.resignation_request(id),
    termination_id      UUID         REFERENCES lifecycle.termination_request(id),
    exit_reason         TEXT,
    case_status         VARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    case_owner          VARCHAR(100),
    last_working_date   DATE,
    access_removal_date DATE,
    settlement_date     DATE,
    notes               TEXT,
    created_by          VARCHAR(100),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT offboarding_source_chk CHECK (source IN ('RESIGNATION','TERMINATION','OTHER')),
    CONSTRAINT offboarding_status_chk CHECK (case_status IN (
        'DRAFT','SUBMITTED','PENDING_APPROVAL','APPROVED','IN_PROGRESS',
        'UNDER_NOTICE','CLEARANCE_PENDING','SETTLEMENT_PENDING',
        'COMPLETED','CANCELLED','REVERSED')),
    CONSTRAINT offboarding_one_active_per_employee UNIQUE NULLS NOT DISTINCT (
        employee_id, resignation_id) -- prevent duplicate cases per resignation
);

CREATE INDEX idx_offboarding_employee ON lifecycle.offboarding_case(employee_id);
CREATE INDEX idx_offboarding_status   ON lifecycle.offboarding_case(case_status);
CREATE INDEX idx_offboarding_lwd      ON lifecycle.offboarding_case(last_working_date);

-- RESIGNATION_APPROVAL workflow (PRD §7): Line Manager → HR
INSERT INTO workflow.workflow_definition (id, code, name, description, auto_approve, active, created_at, updated_at)
VALUES (
    'bb130313-0000-0000-0000-000000000001',
    'RESIGNATION_APPROVAL',
    'Resignation Approval',
    'Two-step approval for employee resignation requests (PRD §7): line manager confirms, HR finalises the last working day.',
    FALSE,
    TRUE,
    now(), now()
);
INSERT INTO workflow.workflow_step (id, definition_id, step_order, name, approver_role,
    resolves_to_manager, resolves_to_hrbp, sla_hours, escalation_action, escalation_to, parallel, condition_spel)
VALUES
    ('bb130313-0000-0000-0001-000000000001', 'bb130313-0000-0000-0000-000000000001',
     1, 'Line Manager review', 'ROLE_HR_SPECIALIST', false, false, 48, 'NOTIFY', null, false, null),
    ('bb130313-0000-0000-0001-000000000002', 'bb130313-0000-0000-0000-000000000001',
     2, 'HR finalisation', 'ROLE_HR_ADMIN', false, false, 48, 'NOTIFY', null, false, null);
