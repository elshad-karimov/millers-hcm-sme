-- HCM_20 M428 — Budget control rules (PRD §20.6).
-- Defines rules that WARN or BLOCK when a salary change / new hire / overtime
-- event would exceed budget thresholds. Hooked into SalaryChangeRequestService
-- and OfferService (non-fatally: WARN → include warning in response/audit).

CREATE TABLE IF NOT EXISTS budgeting.budget_control_rule (
    id              UUID PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL DEFAULT 'default',
    trigger_point   VARCHAR(30) NOT NULL,
        -- SALARY_CHANGE | NEW_HIRE | OVERTIME | TRAINING
    action          VARCHAR(20) NOT NULL DEFAULT 'WARN',
        -- WARN | BLOCK
    threshold_pct   NUMERIC(5, 2) NOT NULL DEFAULT 100,  -- % of budget
    active          BOOLEAN NOT NULL DEFAULT true,
    created_by      VARCHAR(80),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_budget_ctrl_trigger UNIQUE (tenant_id, trigger_point),
    CONSTRAINT chk_budget_ctrl_trigger CHECK (trigger_point IN ('SALARY_CHANGE', 'NEW_HIRE', 'OVERTIME', 'TRAINING')),
    CONSTRAINT chk_budget_ctrl_action CHECK (action IN ('WARN', 'BLOCK'))
);
CREATE INDEX idx_budget_ctrl_tenant ON budgeting.budget_control_rule (tenant_id, active);

COMMENT ON TABLE budgeting.budget_control_rule IS 'HCM_20 M428 — budget control rules (PRD §20.6)';
