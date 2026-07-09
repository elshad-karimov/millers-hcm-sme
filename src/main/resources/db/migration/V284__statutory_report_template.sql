-- ----------------------------------------------------------------------------
-- M468 — Statutory report templates (compliance module foundation)
--
-- Defines templates for statutory reports (monthly/quarterly/annual tax filings).
-- Seed 5 Azerbaijan templates: tax, DSMF, MMI, unemployment (monthly), annual summary.
-- ----------------------------------------------------------------------------

CREATE SCHEMA IF NOT EXISTS compliance;

CREATE TABLE compliance.statutory_report_template (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id      VARCHAR(64) NOT NULL DEFAULT 'default',
  code           VARCHAR(60) NOT NULL,
  name           VARCHAR(255) NOT NULL,
  country        VARCHAR(2) NOT NULL DEFAULT 'AZ',
  frequency      VARCHAR(20) NOT NULL,
  file_format    VARCHAR(10) NOT NULL,
  due_day        INT NOT NULL DEFAULT 20,
  description    TEXT,
  active         BOOLEAN NOT NULL DEFAULT true,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by     VARCHAR(200),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by     VARCHAR(200),

  CONSTRAINT statutory_report_template_code_uq UNIQUE (tenant_id, code),
  CONSTRAINT statutory_report_template_freq_ck CHECK (frequency IN ('MONTHLY', 'QUARTERLY', 'ANNUAL')),
  CONSTRAINT statutory_report_template_fmt_ck CHECK (file_format IN ('XLSX', 'CSV'))
);

CREATE INDEX idx_statutory_template_tenant ON compliance.statutory_report_template(tenant_id, active);

-- Seed 5 Azerbaijan statutory report templates
INSERT INTO compliance.statutory_report_template
  (id, tenant_id, code, name, country, frequency, file_format, due_day, description, active)
VALUES
  (
    'aaaa1111-2222-3333-4444-555566667701',
    'default',
    'AZ_TAX_MONTHLY',
    'Monthly Income Tax Report',
    'AZ',
    'MONTHLY',
    'XLSX',
    20,
    'Monthly income tax withholding report to State Tax Service',
    true
  ),
  (
    'aaaa1111-2222-3333-4444-555566667702',
    'default',
    'AZ_DSMF_MONTHLY',
    'Monthly DSMF Contributions Report',
    'AZ',
    'MONTHLY',
    'XLSX',
    20,
    'Monthly State Social Protection Fund (DSMF) contributions report',
    true
  ),
  (
    'aaaa1111-2222-3333-4444-555566667703',
    'default',
    'AZ_MMI_MONTHLY',
    'Monthly MMI Contributions Report',
    'AZ',
    'MONTHLY',
    'XLSX',
    20,
    'Monthly Mandatory Medical Insurance (MMI) contributions report',
    true
  ),
  (
    'aaaa1111-2222-3333-4444-555566667704',
    'default',
    'AZ_UNEMP_MONTHLY',
    'Monthly Unemployment Insurance Report',
    'AZ',
    'MONTHLY',
    'XLSX',
    20,
    'Monthly unemployment insurance contributions report',
    true
  ),
  (
    'aaaa1111-2222-3333-4444-555566667705',
    'default',
    'AZ_ANNUAL_SUMMARY',
    'Annual Payroll Summary Report',
    'AZ',
    'ANNUAL',
    'XLSX',
    31,
    'Annual summary of payroll and tax withholdings (due 31 January)',
    true
  )
ON CONFLICT (tenant_id, code) DO NOTHING;
