-- M326: Attendance Policy Engine (PRD §2 / §25 / §26)
-- Replaces all hard-coded attendance rules with a configurable policy entity.
-- Resolved blocker decisions:
--   late/early per-minute rate = monthly_salary / 30 / 480
--   absence daily rate = monthly_salary / 30
--   correction approval = manager always + HR when period_locked OR absence_changed OR ot_delta > 30 min

CREATE TABLE attendance.attendance_policy (
    id                                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                           UUID        NOT NULL,
    code                                VARCHAR(50) NOT NULL,
    name                                VARCHAR(200) NOT NULL,
    description                         TEXT,

    -- Applicability (NULL = applies to all)
    department_id                       UUID,
    location_id                         UUID,
    employment_type                     VARCHAR(50),

    -- Grace periods (minutes)
    clock_in_grace_minutes              INT         NOT NULL DEFAULT 10,
    clock_out_grace_minutes             INT         NOT NULL DEFAULT 0,

    -- Late rules (resolved BLOCKER 1)
    late_deduction_enabled              BOOLEAN     NOT NULL DEFAULT FALSE,
    late_max_before_half_day_minutes    INT         NOT NULL DEFAULT 120,
    late_monthly_threshold_minutes      INT         NOT NULL DEFAULT 0,

    -- Early leave rules (resolved BLOCKER 3)
    early_leave_deduction_enabled       BOOLEAN     NOT NULL DEFAULT FALSE,
    early_leave_treatment               VARCHAR(30) NOT NULL DEFAULT 'SALARY_DEDUCTION',
    early_leave_tolerance_minutes       INT         NOT NULL DEFAULT 0,

    -- Absence rules (resolved BLOCKER 2)
    absence_deduction_enabled           BOOLEAN     NOT NULL DEFAULT TRUE,
    absence_daily_divisor               INT         NOT NULL DEFAULT 30,
    hours_per_day                       INT         NOT NULL DEFAULT 8,
    unauthorized_absence_treatment      VARCHAR(30) NOT NULL DEFAULT 'UNPAID',

    -- OT rules
    overtime_requires_preapproval       BOOLEAN     NOT NULL DEFAULT FALSE,
    overtime_dept_head_threshold_minutes INT        NOT NULL DEFAULT 120,
    comp_off_enabled                    BOOLEAN     NOT NULL DEFAULT FALSE,

    -- Break rules
    auto_break_deduction_enabled        BOOLEAN     NOT NULL DEFAULT FALSE,
    break_minutes                       INT         NOT NULL DEFAULT 60,
    min_hours_for_break_deduction       DECIMAL(4,2) NOT NULL DEFAULT 6.0,

    -- Rounding (default off — §26 deferred)
    rounding_enabled                    BOOLEAN     NOT NULL DEFAULT FALSE,
    rounding_minutes                    INT         NOT NULL DEFAULT 15,
    rounding_direction                  VARCHAR(10) NOT NULL DEFAULT 'NEAREST',

    active                              BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at                          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by                          VARCHAR(200),
    updated_by                          VARCHAR(200),

    CONSTRAINT uq_attendance_policy_code UNIQUE (tenant_id, code)
);

CREATE INDEX idx_att_policy_tenant    ON attendance.attendance_policy (tenant_id);
CREATE INDEX idx_att_policy_dept      ON attendance.attendance_policy (department_id) WHERE department_id IS NOT NULL;
CREATE INDEX idx_att_policy_location  ON attendance.attendance_policy (location_id)   WHERE location_id   IS NOT NULL;

-- Link daily_summary to the resolved policy (audit trail — which rules were applied)
ALTER TABLE attendance.daily_summary
    ADD COLUMN IF NOT EXISTS policy_id UUID REFERENCES attendance.attendance_policy(id),
    ADD COLUMN IF NOT EXISTS calculation_status VARCHAR(30) NOT NULL DEFAULT 'COMPUTED',
    ADD COLUMN IF NOT EXISTS approved_at        TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS approved_by        VARCHAR(200);

-- Attendance correction requests with proper workflow (resolved BLOCKER 4)
CREATE TABLE attendance.attendance_correction_request (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL,
    employee_id     UUID        NOT NULL,
    work_date       DATE        NOT NULL,
    summary_id      UUID        NOT NULL,   -- references attendance.daily_summary(id) — no FK: parent is partitioned (composite PK)
    policy_id       UUID        REFERENCES attendance.attendance_policy(id),

    -- What the employee is requesting to change
    requested_clock_in      TIMESTAMPTZ,
    requested_clock_out     TIMESTAMPTZ,
    requested_status        VARCHAR(30),
    reason                  TEXT        NOT NULL,
    correction_type         VARCHAR(30) NOT NULL DEFAULT 'CLOCK_TIME',

    -- Flags for conditional HR step (BLOCKER 4 resolution)
    absence_status_changed  BOOLEAN     NOT NULL DEFAULT FALSE,
    overtime_delta_minutes  INT         NOT NULL DEFAULT 0,

    workflow_instance_id    UUID,
    workflow_status         VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    decision                VARCHAR(30),
    decision_comment        TEXT,
    decided_at              TIMESTAMPTZ,
    decided_by              VARCHAR(200),

    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(200),
    updated_by  VARCHAR(200)
);

CREATE INDEX idx_att_corr_tenant_emp   ON attendance.attendance_correction_request (tenant_id, employee_id);
CREATE INDEX idx_att_corr_work_date    ON attendance.attendance_correction_request (work_date);
CREATE INDEX idx_att_corr_wf_status    ON attendance.attendance_correction_request (workflow_status);

-- Seed workflow definitions for attendance correction and OT request
INSERT INTO workflow.workflow_definition (id, code, name, description, active)
VALUES
    (gen_random_uuid(), 'ATTENDANCE_CORRECTION', 'Attendance Correction Approval',
     'Conditional 2-step: manager always; HR when period locked, absence changed, or OT delta > 30 min', true),
    (gen_random_uuid(), 'OVERTIME_REQUEST',      'Overtime Request Approval',
     'Manager always; dept-head conditional on OT > 2h', true)
ON CONFLICT (code) DO NOTHING;

-- Workflow steps for ATTENDANCE_CORRECTION
-- Step 1: manager always (resolves_to_manager); Step 2: HR conditional
WITH wf AS (SELECT id FROM workflow.workflow_definition WHERE code = 'ATTENDANCE_CORRECTION')
INSERT INTO workflow.workflow_step
    (id, definition_id, step_order, name, approver_role, resolves_to_manager, resolves_to_hrbp, sla_hours, escalation_action, escalation_to, parallel, condition_spel)
SELECT gen_random_uuid(), wf.id, 1, 'Manager Approval', 'ROLE_DEPARTMENT_MANAGER', true,  false, 48, 'NOTIFY', null, false, null              FROM wf
UNION ALL
SELECT gen_random_uuid(), wf.id, 2, 'HR Review',        'ROLE_HR_ADMIN',           false, false, 48, 'NOTIFY', null, false,
       'periodLocked or absenceStatusChanged or overtimeDeltaMinutes > 30' FROM wf;

-- Workflow steps for OVERTIME_REQUEST
WITH wf AS (SELECT id FROM workflow.workflow_definition WHERE code = 'OVERTIME_REQUEST')
INSERT INTO workflow.workflow_step
    (id, definition_id, step_order, name, approver_role, resolves_to_manager, resolves_to_hrbp, sla_hours, escalation_action, escalation_to, parallel, condition_spel)
SELECT gen_random_uuid(), wf.id, 1, 'Manager Approval',   'ROLE_DEPARTMENT_MANAGER', true,  false, 48, 'NOTIFY', null, false, null                    FROM wf
UNION ALL
SELECT gen_random_uuid(), wf.id, 2, 'Dept-Head Approval', 'ROLE_HR_ADMIN',           false, false, 48, 'NOTIFY', null, false, 'overtimeMinutes > 120' FROM wf;

-- Seed a default catch-all policy (no department/location/type filter = applies to all)
-- Tenant_id = first legal entity (dev seed only — production sets this per-tenant)
INSERT INTO attendance.attendance_policy (
    id, tenant_id, code, name, description,
    clock_in_grace_minutes, late_deduction_enabled, absence_deduction_enabled,
    absence_daily_divisor, hours_per_day, overtime_requires_preapproval, active
)
SELECT
    gen_random_uuid(),
    (SELECT id FROM organization.legal_entity LIMIT 1),
    'STANDARD',
    'Standard Attendance Policy',
    'Default catch-all policy. Grace period 10 min. Absence deduction enabled (salary / 30). Late deduction disabled by default.',
    10, false, true, 30, 8, false, true
WHERE EXISTS (SELECT 1 FROM organization.legal_entity LIMIT 1);
