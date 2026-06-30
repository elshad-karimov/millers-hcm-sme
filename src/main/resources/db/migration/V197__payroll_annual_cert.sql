-- V197 — Year-End Processing + AZ Annual Tax Certificate (M354)
--
-- annual_payroll_summary aggregates all PAID REGULAR runs for a calendar year.
-- annual_tax_certificate stores the AZ income certificate (Form 154 equivalent)
-- with PDF stored in MinIO.

CREATE TABLE payroll.annual_payroll_summary (
    id                   UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            VARCHAR(64)   NOT NULL DEFAULT 'default',
    employee_id          UUID          NOT NULL REFERENCES core_hr.employee (id),
    year                 INTEGER       NOT NULL,
    total_gross          NUMERIC(12, 2) NOT NULL,
    total_income_tax     NUMERIC(12, 2) NOT NULL,
    total_dsmf_employee  NUMERIC(12, 2) NOT NULL,
    total_mmi_employee   NUMERIC(12, 2) NOT NULL,
    total_unemployment   NUMERIC(12, 2) NOT NULL,
    total_net            NUMERIC(12, 2) NOT NULL,
    months_count         INTEGER       NOT NULL,
    generated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uq_aps_employee_year UNIQUE (tenant_id, employee_id, year)
);

CREATE INDEX idx_aps_tenant_year ON payroll.annual_payroll_summary (tenant_id, year);
CREATE INDEX idx_aps_employee    ON payroll.annual_payroll_summary (employee_id);

CREATE TABLE payroll.annual_tax_certificate (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           VARCHAR(64)   NOT NULL DEFAULT 'default',
    employee_id         UUID          NOT NULL REFERENCES core_hr.employee (id),
    year                INTEGER       NOT NULL,
    annual_gross        NUMERIC(12, 2) NOT NULL,
    exempt_amount       NUMERIC(12, 2) NOT NULL,
    taxable_gross       NUMERIC(12, 2) NOT NULL,
    total_tax_withheld  NUMERIC(12, 2) NOT NULL,
    status              VARCHAR(20)   NOT NULL DEFAULT 'GENERATED'
        CONSTRAINT annual_tax_cert_status_ck CHECK (status IN ('GENERATED', 'DELIVERED')),
    generated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    storage_path        VARCHAR(1000),

    CONSTRAINT uq_atc_employee_year UNIQUE (tenant_id, employee_id, year)
);

CREATE INDEX idx_atc_tenant_year ON payroll.annual_tax_certificate (tenant_id, year);
CREATE INDEX idx_atc_employee    ON payroll.annual_tax_certificate (employee_id);
