-- M359–M360: Compensation Management Phase A — foundation schema
-- Schema: compensation. (config, pay bands, change reasons)

CREATE SCHEMA IF NOT EXISTS compensation;

-- ── M359: Tenant compensation configuration ─────────────────────────────────
CREATE TABLE compensation.comp_config (
    id                                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                           VARCHAR(64) NOT NULL DEFAULT 'default',
    max_increase_pct_without_approval   NUMERIC(5, 2) NOT NULL DEFAULT 15.00,
    budget_exceeded_policy              VARCHAR(30) NOT NULL DEFAULT 'WARNING'
        CHECK (budget_exceeded_policy IN ('WARNING', 'HARD_STOP', 'EXCEPTION_APPROVAL')),
    default_currency                    VARCHAR(3) NOT NULL DEFAULT 'AZN',
    created_at                          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id)
);

-- Seed the default tenant config
INSERT INTO compensation.comp_config (tenant_id, max_increase_pct_without_approval, budget_exceeded_policy, default_currency)
VALUES ('default', 15.00, 'WARNING', 'AZN');

-- ── M359: Pay bands (grade-linked salary ranges) ────────────────────────────
CREATE TABLE compensation.pay_band (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(64) NOT NULL DEFAULT 'default',
    code            VARCHAR(50) NOT NULL,
    name            VARCHAR(160) NOT NULL,
    grade_id        UUID REFERENCES staffing.grade(id),
    currency        VARCHAR(3) NOT NULL DEFAULT 'AZN',
    min_salary      NUMERIC(14, 2) NOT NULL,
    mid_salary      NUMERIC(14, 2) NOT NULL,
    max_salary      NUMERIC(14, 2) NOT NULL,
    q1_salary       NUMERIC(14, 2),
    q3_salary       NUMERIC(14, 2),
    effective_from  DATE NOT NULL,
    effective_to    DATE,
    is_active       BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, code),
    CHECK (min_salary <= mid_salary AND mid_salary <= max_salary),
    CHECK (effective_to IS NULL OR effective_from <= effective_to)
);

CREATE INDEX idx_pay_band_tenant_grade ON compensation.pay_band (tenant_id, grade_id);
CREATE INDEX idx_pay_band_tenant_active ON compensation.pay_band (tenant_id, is_active) WHERE is_active = true;

-- ── M360: Change reasons (why salary changed) ───────────────────────────────
CREATE TABLE compensation.change_reason (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           VARCHAR(64) NOT NULL DEFAULT 'default',
    code                VARCHAR(50) NOT NULL,
    name                VARCHAR(160) NOT NULL,
    affects_workflow    BOOLEAN NOT NULL DEFAULT true,
    category            VARCHAR(40),
    is_active           BOOLEAN NOT NULL DEFAULT true,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, code)
);

CREATE INDEX idx_change_reason_tenant_active ON compensation.change_reason (tenant_id, is_active) WHERE is_active = true;

-- Seed standard change reasons
INSERT INTO compensation.change_reason (tenant_id, code, name, category, affects_workflow)
VALUES
    ('default', 'ANNUAL_MERIT', 'Annual Merit Increase', 'MERIT', true),
    ('default', 'PROMOTION', 'Promotion', 'PROMOTION', true),
    ('default', 'MARKET_ADJUSTMENT', 'Market Adjustment', 'MARKET', true),
    ('default', 'RETENTION', 'Retention Adjustment', 'RETENTION', true),
    ('default', 'EQUITY_ADJUSTMENT', 'Internal Equity Adjustment', 'ADJUSTMENT', true),
    ('default', 'CORRECTION', 'Administrative Correction', 'OTHER', false);
