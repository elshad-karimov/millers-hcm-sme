-- V193 — Salary Component Catalog + PayrollEngine Integration (M349)
--
-- Introduces configurable salary components (EARNING/DEDUCTION) with per-employee
-- effective-dated assignments and a snapshot table for per-payslip line items.
-- Also extends payroll_run for off-cycle support and payroll_deduction for
-- advance/loan traceability.

-- 1. Salary component catalog
CREATE TABLE payroll.salary_component (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           VARCHAR(64)   NOT NULL DEFAULT 'default',
    code                VARCHAR(50)   NOT NULL,
    name                VARCHAR(200)  NOT NULL,
    kind                VARCHAR(20)   NOT NULL
        CONSTRAINT salary_component_kind_ck CHECK (kind IN ('EARNING', 'DEDUCTION')),
    calculation_method  VARCHAR(30)   NOT NULL
        CONSTRAINT salary_component_calc_ck CHECK (calculation_method IN ('FIXED_AMOUNT', 'PERCENTAGE_OF_BASE', 'FLAT_RATE')),
    default_amount      NUMERIC(12, 2),
    percentage          NUMERIC(5, 4),          -- e.g. 0.1000 = 10% of base salary
    is_taxable          BOOLEAN       NOT NULL DEFAULT TRUE,
    contribution_exempt BOOLEAN       NOT NULL DEFAULT FALSE,  -- TRUE = also exempt from DSMF/MMI base
    is_statutory        BOOLEAN       NOT NULL DEFAULT FALSE,
    is_active           BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uq_salary_component_code UNIQUE (tenant_id, code)
);

CREATE INDEX idx_salary_component_tenant ON payroll.salary_component (tenant_id, is_active);

-- 2. Per-employee component assignments (effective-dated)
CREATE TABLE payroll.salary_component_assignment (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(64)  NOT NULL DEFAULT 'default',
    employee_id     UUID         NOT NULL REFERENCES core_hr.employee (id),
    component_id    UUID         NOT NULL REFERENCES payroll.salary_component (id),
    amount_override NUMERIC(12, 2),
    effective_from  DATE         NOT NULL,
    effective_to    DATE,
    reason          VARCHAR(500),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_sca_dates CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT uq_sca_employee_component_from UNIQUE (tenant_id, employee_id, component_id, effective_from)
);

CREATE INDEX idx_sca_tenant_employee ON payroll.salary_component_assignment (tenant_id, employee_id);
CREATE INDEX idx_sca_component       ON payroll.salary_component_assignment (component_id);

-- 3. Per-payslip component snapshot (audit trail for each payroll line item)
CREATE TABLE payroll.payroll_result_component (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           VARCHAR(64)  NOT NULL DEFAULT 'default',
    result_id           UUID         NOT NULL REFERENCES payroll.payroll_result (id) ON DELETE CASCADE,
    component_id        UUID         REFERENCES payroll.salary_component (id),
    component_code      VARCHAR(50)  NOT NULL,
    component_name      VARCHAR(200) NOT NULL,
    kind                VARCHAR(20)  NOT NULL,
    amount              NUMERIC(12, 2) NOT NULL,
    is_taxable          BOOLEAN      NOT NULL,
    contribution_exempt BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_prc_result      ON payroll.payroll_result_component (result_id);
CREATE INDEX idx_prc_tenant      ON payroll.payroll_result_component (tenant_id, result_id);

-- 4. Extend payroll_run for off-cycle support
ALTER TABLE payroll.payroll_run
    ADD COLUMN IF NOT EXISTS run_type     VARCHAR(20) NOT NULL DEFAULT 'REGULAR'
        CONSTRAINT payroll_run_type_ck CHECK (run_type IN ('REGULAR', 'OFF_CYCLE')),
    ADD COLUMN IF NOT EXISTS description  VARCHAR(500),
    ADD COLUMN IF NOT EXISTS employee_ids UUID[];

-- 5. Extend payroll_deduction for advance/loan FK traceability
ALTER TABLE payroll.payroll_deduction
    ADD COLUMN IF NOT EXISTS source_advance_id UUID,
    ADD COLUMN IF NOT EXISTS source_loan_id    UUID;

-- 6. Seed informational statutory components (actual amounts from StatutoryCalculator)
INSERT INTO payroll.salary_component
    (tenant_id, code, name, kind, calculation_method, is_taxable, contribution_exempt, is_statutory, is_active)
VALUES
    ('default', 'INCOME_TAX',           'Income Tax',                'DEDUCTION', 'FIXED_AMOUNT', FALSE, TRUE,  TRUE, TRUE),
    ('default', 'DSMF_EMPLOYEE',        'DSMF (Employee)',           'DEDUCTION', 'FIXED_AMOUNT', FALSE, TRUE,  TRUE, TRUE),
    ('default', 'MMI_EMPLOYEE',         'MMI (Employee)',            'DEDUCTION', 'FIXED_AMOUNT', FALSE, TRUE,  TRUE, TRUE),
    ('default', 'UNEMPLOYMENT_EMPLOYEE','Unemployment (Employee)',   'DEDUCTION', 'FIXED_AMOUNT', FALSE, TRUE,  TRUE, TRUE),
    ('default', 'DSMF_EMPLOYER',        'DSMF (Employer Contrib.)',  'DEDUCTION', 'FIXED_AMOUNT', FALSE, TRUE,  TRUE, TRUE)
ON CONFLICT (tenant_id, code) DO NOTHING;
