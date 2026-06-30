-- V198 — Cost Center Allocation + GL Journal Generation (M355)
--
-- cost_center_allocation: effective-dated splits per employee (sum must = 100%).
-- payroll_result_cost_split: snapshot of how each employee's payroll was split.
-- gl_account_mapping: maps component_kind/code → debit/credit GL accounts.
-- gl_journal + gl_journal_line: balanced double-entry journal generated on APPROVED.

-- 1. Employee cost center allocation
CREATE TABLE payroll.cost_center_allocation (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        VARCHAR(64)  NOT NULL DEFAULT 'default',
    employee_id      UUID         NOT NULL REFERENCES core_hr.employee (id),
    cost_center_code VARCHAR(100) NOT NULL,
    allocation_pct   NUMERIC(5, 2) NOT NULL
        CONSTRAINT chk_cca_pct CHECK (allocation_pct > 0 AND allocation_pct <= 100),
    effective_from   DATE         NOT NULL,
    effective_to     DATE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_cca_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE INDEX idx_cca_tenant_employee ON payroll.cost_center_allocation (tenant_id, employee_id);

-- 2. Per-result cost split snapshot
CREATE TABLE payroll.payroll_result_cost_split (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        VARCHAR(64)  NOT NULL DEFAULT 'default',
    result_id        UUID         NOT NULL REFERENCES payroll.payroll_result (id) ON DELETE CASCADE,
    cost_center_code VARCHAR(100) NOT NULL,
    gross_amount     NUMERIC(12, 2) NOT NULL,
    net_amount       NUMERIC(12, 2) NOT NULL,
    allocation_pct   NUMERIC(5, 2) NOT NULL
);

CREATE INDEX idx_prcs_result ON payroll.payroll_result_cost_split (result_id);

-- 3. GL account mapping
CREATE TABLE payroll.gl_account_mapping (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        VARCHAR(64)  NOT NULL DEFAULT 'default',
    component_kind   VARCHAR(20),
    component_code   VARCHAR(50),
    account_type     VARCHAR(20)  NOT NULL
        CONSTRAINT gl_account_mapping_type_ck CHECK (account_type IN ('DEBIT', 'CREDIT')),
    gl_account_code  VARCHAR(50)  NOT NULL,
    gl_account_name  VARCHAR(200) NOT NULL,
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,

    CONSTRAINT uq_glam UNIQUE (tenant_id, component_kind, component_code, account_type)
);

CREATE INDEX idx_glam_tenant ON payroll.gl_account_mapping (tenant_id, is_active);

-- 4. GL journal header (one per run; generated on APPROVED)
CREATE TABLE payroll.gl_journal (
    id           UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    VARCHAR(64)   NOT NULL DEFAULT 'default',
    run_id       UUID          NOT NULL REFERENCES payroll.payroll_run (id),
    journal_date DATE          NOT NULL,
    status       VARCHAR(20)   NOT NULL DEFAULT 'DRAFT'
        CONSTRAINT gl_journal_status_ck CHECK (status IN ('DRAFT', 'POSTED')),
    total_debit  NUMERIC(12, 2) NOT NULL,
    total_credit NUMERIC(12, 2) NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by   VARCHAR(200),

    CONSTRAINT uq_glj_run UNIQUE (run_id)
);

CREATE INDEX idx_glj_tenant ON payroll.gl_journal (tenant_id, journal_date);

-- 5. GL journal lines
CREATE TABLE payroll.gl_journal_line (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        VARCHAR(64)   NOT NULL DEFAULT 'default',
    journal_id       UUID          NOT NULL REFERENCES payroll.gl_journal (id) ON DELETE CASCADE,
    sequence_no      INTEGER       NOT NULL,
    cost_center_code VARCHAR(100),
    component_kind   VARCHAR(20),
    component_code   VARCHAR(50),
    account_type     VARCHAR(20)   NOT NULL,
    gl_account_code  VARCHAR(50)   NOT NULL,
    gl_account_name  VARCHAR(200)  NOT NULL,
    amount           NUMERIC(12, 2) NOT NULL,
    description      VARCHAR(500)
);

CREATE INDEX idx_gljl_journal ON payroll.gl_journal_line (journal_id);
