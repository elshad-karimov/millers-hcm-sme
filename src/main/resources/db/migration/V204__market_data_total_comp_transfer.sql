-- V204 — Compensation Phase E: Market Data, Total Comp Statement, Payroll Transfer
-- M367: Market salary survey + data
-- M368: Total compensation statement
-- M369: Compensation → Payroll transfer

-- ══════════════════════════════════════════════════════════════════════════════
-- M367 — Market Salary Survey + Data
-- ══════════════════════════════════════════════════════════════════════════════

CREATE TABLE compensation.market_salary_survey (
    id                 UUID PRIMARY KEY,
    tenant_id          VARCHAR(64) NOT NULL DEFAULT 'default',
    provider           VARCHAR(120) NOT NULL,
    survey_year        INT NOT NULL,
    country            VARCHAR(60) DEFAULT 'Azerbaijan',
    currency           VARCHAR(3) DEFAULT 'AZN',
    notes              TEXT,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,

    UNIQUE (tenant_id, provider, survey_year)
);

CREATE INDEX idx_market_salary_survey_tenant ON compensation.market_salary_survey (tenant_id, survey_year DESC);

CREATE TABLE compensation.market_salary_data (
    id                 UUID PRIMARY KEY,
    tenant_id          VARCHAR(64) NOT NULL DEFAULT 'default',
    survey_id          UUID NOT NULL REFERENCES compensation.market_salary_survey (id) ON DELETE CASCADE,
    job_code           VARCHAR(60),
    grade_code         VARCHAR(60),
    location           VARCHAR(80),
    p25                NUMERIC(14, 2),
    p50                NUMERIC(14, 2),
    p75                NUMERIC(14, 2),
    p90                NUMERIC(14, 2),
    currency           VARCHAR(3) DEFAULT 'AZN',
    created_at         TIMESTAMPTZ NOT NULL,

    CHECK (p25 <= p50 AND p50 <= p75 AND p75 <= p90)
);

CREATE INDEX idx_market_salary_data_tenant_survey ON compensation.market_salary_data (tenant_id, survey_id);
CREATE INDEX idx_market_salary_data_grade ON compensation.market_salary_data (tenant_id, grade_code, job_code);

-- ══════════════════════════════════════════════════════════════════════════════
-- M368 — Total Compensation Statement
-- ══════════════════════════════════════════════════════════════════════════════

CREATE TABLE compensation.total_comp_statement (
    id                              UUID PRIMARY KEY,
    tenant_id                       VARCHAR(64) NOT NULL DEFAULT 'default',
    employee_id                     UUID NOT NULL,
    year                            INT NOT NULL,
    base_salary                     NUMERIC(14, 2) NOT NULL DEFAULT 0,
    allowances_total                NUMERIC(14, 2) NOT NULL DEFAULT 0,
    bonus_total                     NUMERIC(14, 2) NOT NULL DEFAULT 0,
    incentives_total                NUMERIC(14, 2) NOT NULL DEFAULT 0,
    commission_total                NUMERIC(14, 2) NOT NULL DEFAULT 0,
    employer_benefits_total         NUMERIC(14, 2) NOT NULL DEFAULT 0,
    employer_contributions_total    NUMERIC(14, 2) NOT NULL DEFAULT 0,
    total_comp                      NUMERIC(14, 2) NOT NULL DEFAULT 0,
    currency                        VARCHAR(3) DEFAULT 'AZN',
    status                          VARCHAR(20) NOT NULL DEFAULT 'GENERATED' CHECK (status IN ('GENERATED', 'RELEASED')),
    storage_path                    VARCHAR(1000),
    generated_at                    TIMESTAMPTZ NOT NULL,
    released_at                     TIMESTAMPTZ,

    UNIQUE (tenant_id, employee_id, year)
);

CREATE INDEX idx_total_comp_statement_tenant_year ON compensation.total_comp_statement (tenant_id, year DESC);
CREATE INDEX idx_total_comp_statement_employee ON compensation.total_comp_statement (employee_id, year DESC);

-- ══════════════════════════════════════════════════════════════════════════════
-- M369 — Compensation → Payroll Transfer
-- ══════════════════════════════════════════════════════════════════════════════

CREATE TABLE compensation.payroll_comp_transfer (
    id                 UUID PRIMARY KEY,
    tenant_id          VARCHAR(64) NOT NULL DEFAULT 'default',
    source_type        VARCHAR(20) NOT NULL CHECK (source_type IN ('INCENTIVE', 'COMMISSION')),
    source_id          UUID NOT NULL,
    employee_id        UUID NOT NULL,
    target_run_id      UUID NOT NULL,
    amount             NUMERIC(14, 2) NOT NULL,
    payroll_bonus_id   UUID,
    status             VARCHAR(20) NOT NULL DEFAULT 'TRANSFERRED' CHECK (status IN ('TRANSFERRED', 'FAILED')),
    transferred_at     TIMESTAMPTZ NOT NULL,
    transferred_by     VARCHAR(100),

    UNIQUE (source_type, source_id)
);

CREATE INDEX idx_payroll_comp_transfer_tenant ON compensation.payroll_comp_transfer (tenant_id, transferred_at DESC);
CREATE INDEX idx_payroll_comp_transfer_run ON compensation.payroll_comp_transfer (target_run_id);
CREATE INDEX idx_payroll_comp_transfer_source ON compensation.payroll_comp_transfer (source_type, source_id);
