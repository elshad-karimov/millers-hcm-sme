-- M158: Payroll ERP / Accounting journal-entry export (PRD §17.2).
--
-- When a payroll run reaches APPROVED/CLOSED status, HR Finance can generate
-- a journal-entry export batch for upload into an ERP system (1C, Dynamics 365,
-- or generic CSV).  Each batch contains one debit/credit line per account code.
--
-- Format types: CSV_GENERIC, CSV_1C, CSV_DYNAMICS365, JSON
-- Status lifecycle: PENDING → GENERATING → READY | FAILED

CREATE TABLE payroll.erp_export (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    export_no       VARCHAR(20)  NOT NULL UNIQUE,          -- EXP-00001
    run_id          UUID         NOT NULL REFERENCES payroll.payroll_run(id),
    format          VARCHAR(30)  NOT NULL DEFAULT 'CSV_GENERIC'
                        CHECK (format IN ('CSV_GENERIC','CSV_1C','CSV_DYNAMICS365','JSON')),
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING','GENERATING','READY','FAILED')),
    posting_date    DATE         NOT NULL,
    reference_no    VARCHAR(100),          -- free-form reference for the ERP
    journal_type    VARCHAR(80),           -- e.g. "Payroll", "Bonus"
    line_count      INT          NOT NULL DEFAULT 0,
    total_debit     NUMERIC(18,2),
    total_credit    NUMERIC(18,2),
    error_message   TEXT,
    file_size_bytes BIGINT,
    created_by      VARCHAR(80),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    generated_at    TIMESTAMPTZ
);

CREATE SEQUENCE payroll.erp_export_seq START 1 INCREMENT 1;
CREATE INDEX idx_erp_export_run ON payroll.erp_export (run_id);

-- Journal lines (double-entry: each entry has a debit and credit side)
CREATE TABLE payroll.erp_export_line (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    export_id       UUID         NOT NULL REFERENCES payroll.erp_export(id) ON DELETE CASCADE,
    line_no         INT          NOT NULL,
    account_code    VARCHAR(50)  NOT NULL,     -- GL account code
    account_name    VARCHAR(200),
    cost_centre     VARCHAR(50),
    description     VARCHAR(500),
    debit           NUMERIC(18,2) NOT NULL DEFAULT 0,
    credit          NUMERIC(18,2) NOT NULL DEFAULT 0,
    currency        CHAR(3)      NOT NULL DEFAULT 'AZN',
    employee_count  INT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_erp_line_export ON payroll.erp_export_line (export_id);
