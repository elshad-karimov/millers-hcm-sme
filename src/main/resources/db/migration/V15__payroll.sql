-- Payroll module (PRD Section 8.9).
-- Tables: employee_compensation (effective-dated salary), payroll_run header,
-- payroll_result per-employee, statutory_rule (versioned, jsonb), payroll_bonus
-- attached to a run, and bank_account for bank-file generation.

CREATE TABLE payroll.employee_compensation (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id           UUID         NOT NULL,
    monthly_base_salary   NUMERIC(14,2) NOT NULL,
    currency              VARCHAR(3)   NOT NULL DEFAULT 'AZN',
    effective_from        DATE         NOT NULL,
    effective_to          DATE,
    reason                TEXT,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by            VARCHAR(120),

    CONSTRAINT chk_compensation_dates CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT chk_compensation_salary CHECK (monthly_base_salary >= 0)
);

CREATE INDEX idx_comp_employee   ON payroll.employee_compensation (employee_id, effective_from);

CREATE TABLE payroll.bank_account (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id     UUID         NOT NULL UNIQUE,
    bank_code       VARCHAR(32),     -- e.g. ABB, KAPITAL, PASHA
    bank_name       VARCHAR(160),
    iban            VARCHAR(40),
    account_number  VARCHAR(40),
    currency        VARCHAR(3)   NOT NULL DEFAULT 'AZN',
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Configurable statutory rules with effective dates (PRD 8.9.3).
-- Rule data lives in JSON so we can future-date new bracket tables
-- without code changes; see V16 seed for the 2026 AZ rules.
CREATE TABLE payroll.statutory_rule (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(64)  NOT NULL,    -- INCOME_TAX_AZ, DSMF_AZ, MMI_AZ, UNEMPLOYMENT_AZ, OVERTIME_AZ
    jurisdiction    VARCHAR(8)   NOT NULL,    -- 'AZ'
    name            VARCHAR(200) NOT NULL,
    effective_from  DATE         NOT NULL,
    effective_to    DATE,
    rule_json       JSONB        NOT NULL,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    UNIQUE (code, jurisdiction, effective_from)
);

CREATE INDEX idx_rule_lookup ON payroll.statutory_rule (code, jurisdiction, effective_from);

CREATE SEQUENCE payroll.run_no_seq START WITH 1;
CREATE SEQUENCE payroll.payslip_no_seq START WITH 1;

CREATE TABLE payroll.payroll_run (
    id                       UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    run_no                   VARCHAR(32)  NOT NULL UNIQUE,

    period_year              INTEGER      NOT NULL,
    period_month             INTEGER      NOT NULL,
    jurisdiction             VARCHAR(8)   NOT NULL DEFAULT 'AZ',
    currency                 VARCHAR(3)   NOT NULL DEFAULT 'AZN',

    -- DRAFT, CALCULATED, UNDER_REVIEW, APPROVED, PAID, CLOSED, REOPENED (PRD 8.9.2)
    status                   VARCHAR(32)  NOT NULL,
    workflow_instance_id     UUID,

    total_gross              NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_income_tax         NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_dsmf_employee      NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_dsmf_employer      NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_mmi_employee       NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_mmi_employer       NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_unempl_employee    NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_unempl_employer    NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_net                NUMERIC(14,2) NOT NULL DEFAULT 0,
    employee_count           INTEGER      NOT NULL DEFAULT 0,

    calculated_at            TIMESTAMPTZ,
    calculated_by            VARCHAR(120),
    approved_at              TIMESTAMPTZ,
    approved_by              VARCHAR(120),
    paid_at                  TIMESTAMPTZ,
    paid_by                  VARCHAR(120),
    closed_at                TIMESTAMPTZ,

    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by               VARCHAR(120),

    UNIQUE (period_year, period_month, jurisdiction),
    CONSTRAINT chk_run_month CHECK (period_month BETWEEN 1 AND 12)
);

CREATE INDEX idx_run_period ON payroll.payroll_run (period_year, period_month);
CREATE INDEX idx_run_status ON payroll.payroll_run (status);

CREATE TABLE payroll.payroll_bonus (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id        UUID         NOT NULL REFERENCES payroll.payroll_run (id) ON DELETE CASCADE,
    employee_id   UUID         NOT NULL,
    -- FIXED, PERCENTAGE, PERFORMANCE, ONE_TIME, KPI, DEPARTMENT, MANUAL, IMPORTED
    bonus_type    VARCHAR(32)  NOT NULL,
    amount        NUMERIC(14,2) NOT NULL,
    note          TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by    VARCHAR(120)
);

CREATE INDEX idx_bonus_run_emp ON payroll.payroll_bonus (run_id, employee_id);

CREATE TABLE payroll.payroll_result (
    id                          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id                      UUID         NOT NULL REFERENCES payroll.payroll_run (id) ON DELETE CASCADE,
    employee_id                 UUID         NOT NULL,
    payslip_no                  VARCHAR(32)  NOT NULL UNIQUE,

    timesheet_id                UUID,
    base_salary                 NUMERIC(14,2) NOT NULL DEFAULT 0,
    worked_hours                NUMERIC(9,2)  NOT NULL DEFAULT 0,
    expected_monthly_hours      NUMERIC(9,2)  NOT NULL DEFAULT 0,
    pro_ration_factor           NUMERIC(6,4)  NOT NULL DEFAULT 1,

    overtime_hours              NUMERIC(9,2)  NOT NULL DEFAULT 0,
    overtime_pay                NUMERIC(14,2) NOT NULL DEFAULT 0,

    bonus_amount                NUMERIC(14,2) NOT NULL DEFAULT 0,
    allowance_amount            NUMERIC(14,2) NOT NULL DEFAULT 0,
    deduction_amount            NUMERIC(14,2) NOT NULL DEFAULT 0,

    gross_amount                NUMERIC(14,2) NOT NULL DEFAULT 0,

    income_tax                  NUMERIC(14,2) NOT NULL DEFAULT 0,
    dsmf_employee               NUMERIC(14,2) NOT NULL DEFAULT 0,
    dsmf_employer               NUMERIC(14,2) NOT NULL DEFAULT 0,
    mmi_employee                NUMERIC(14,2) NOT NULL DEFAULT 0,
    mmi_employer                NUMERIC(14,2) NOT NULL DEFAULT 0,
    unempl_employee             NUMERIC(14,2) NOT NULL DEFAULT 0,
    unempl_employer             NUMERIC(14,2) NOT NULL DEFAULT 0,

    net_amount                  NUMERIC(14,2) NOT NULL DEFAULT 0,

    calculation_details         JSONB,        -- step-by-step for the payroll auditor
    note                        TEXT,

    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    UNIQUE (run_id, employee_id)
);

CREATE INDEX idx_result_run ON payroll.payroll_result (run_id);
CREATE INDEX idx_result_emp ON payroll.payroll_result (employee_id);

-- PAYROLL_APPROVAL workflow (PRD 8.9.2). HR runs the calc → Payroll Specialist
-- reviews → Finance/HR approves → CEO/SYSTEM_ADMIN posts to bank. Roles
-- approximated until the full role catalogue lands.
INSERT INTO workflow.workflow_definition (id, code, name, description, auto_approve, active)
VALUES (
    '77777777-7777-7777-7777-777777777777',
    'PAYROLL_APPROVAL',
    'Payroll Approval',
    'Multi-step approval for a payroll run (PRD 8.9.2). Payroll Specialist and Finance roles approximated until those roles are wired to users.',
    FALSE,
    TRUE
);
INSERT INTO workflow.workflow_step (definition_id, step_order, name, approver_role)
VALUES
    ('77777777-7777-7777-7777-777777777777', 1, 'Payroll review',  'ROLE_HR_SPECIALIST'),
    ('77777777-7777-7777-7777-777777777777', 2, 'Finance / HR',    'ROLE_HR_ADMIN'),
    ('77777777-7777-7777-7777-777777777777', 3, 'Executive sign-off','ROLE_SYSTEM_ADMIN');
