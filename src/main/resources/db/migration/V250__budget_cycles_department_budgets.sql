-- HCM_20 M425 — Budget cycles + department budgets (PRD §20.2/§20.3).
-- Budget cycles define planning periods (ANNUAL/QUARTERLY/ROLLING). Department
-- budgets track salary/headcount/benefits/training/recruitment/overtime allocations
-- per org unit + cycle, with DRAFT→SUBMITTED→APPROVED/REJECTED workflow.

CREATE SCHEMA IF NOT EXISTS budgeting;

CREATE TABLE IF NOT EXISTS budgeting.budget_cycle (
    id              UUID PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL DEFAULT 'default',
    code            VARCHAR(64) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    cycle_type      VARCHAR(20) NOT NULL,  -- ANNUAL | QUARTERLY | ROLLING
    period_start    DATE NOT NULL,
    period_end      DATE NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
        -- DRAFT | OPEN | LOCKED | CLOSED
    submission_deadline DATE,
    created_by      VARCHAR(80),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_budget_cycle_code UNIQUE (tenant_id, code),
    CONSTRAINT chk_budget_cycle_type CHECK (cycle_type IN ('ANNUAL', 'QUARTERLY', 'ROLLING')),
    CONSTRAINT chk_budget_cycle_status CHECK (status IN ('DRAFT', 'OPEN', 'LOCKED', 'CLOSED'))
);
CREATE INDEX idx_budget_cycle_tenant ON budgeting.budget_cycle (tenant_id, status);

CREATE TABLE IF NOT EXISTS budgeting.department_budget (
    id                  UUID PRIMARY KEY,
    tenant_id           VARCHAR(64) NOT NULL DEFAULT 'default',
    cycle_id            UUID NOT NULL REFERENCES budgeting.budget_cycle (id),
    org_unit_id         UUID NOT NULL,  -- FK core_hr.org_unit
    salary_budget       NUMERIC(14, 2) DEFAULT 0 NOT NULL,
    headcount_budget    INTEGER DEFAULT 0 NOT NULL,
    benefits_budget     NUMERIC(14, 2) DEFAULT 0 NOT NULL,
    training_budget     NUMERIC(14, 2) DEFAULT 0 NOT NULL,
    recruitment_budget  NUMERIC(14, 2) DEFAULT 0 NOT NULL,
    overtime_budget     NUMERIC(14, 2) DEFAULT 0 NOT NULL,
    -- total_budget is computed in service as sum of all line budgets
    consumed_amount     NUMERIC(14, 2) DEFAULT 0 NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
        -- DRAFT | SUBMITTED | APPROVED | REJECTED
    workflow_instance_id UUID,  -- workflow.workflow_instance FK (approval)
    approved_by         VARCHAR(80),
    approved_at         TIMESTAMPTZ,
    created_by          VARCHAR(80),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_dept_budget_cycle_org UNIQUE (tenant_id, cycle_id, org_unit_id),
    CONSTRAINT chk_dept_budget_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED'))
);
CREATE INDEX idx_dept_budget_tenant ON budgeting.department_budget (tenant_id, cycle_id, status);
CREATE INDEX idx_dept_budget_org ON budgeting.department_budget (org_unit_id);

COMMENT ON SCHEMA budgeting IS 'HCM_20 M425+ — HR budgeting module (PRD §20)';
COMMENT ON TABLE budgeting.budget_cycle IS 'HCM_20 M425 — budget planning cycles (PRD §20.2)';
COMMENT ON TABLE budgeting.department_budget IS 'HCM_20 M425 — department budget allocations (PRD §20.3)';

-- ── Workflow definition: BUDGET_APPROVAL ─────────────────────────────────────
-- Single HR_ADMIN step (pattern copied from V237 SUCCESSION_PLAN_APPROVAL).

INSERT INTO workflow.workflow_definition (id, code, name, description, auto_approve, active)
VALUES (
    'b0425000-5acc-4001-a001-000000000001'::uuid,
    'BUDGET_APPROVAL',
    'Department Budget Approval',
    'HCM_20 M425 — department budget approval (PRD §20.3). One HR_ADMIN step.',
    FALSE,
    TRUE
);

INSERT INTO workflow.workflow_step (definition_id, step_order, name, approver_role, resolves_to_manager)
VALUES
    ('b0425000-5acc-4001-a001-000000000001'::uuid, 1, 'HR Admin Review', 'ROLE_HR_ADMIN', FALSE);
