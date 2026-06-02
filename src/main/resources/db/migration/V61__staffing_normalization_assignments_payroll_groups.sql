-- M75 / Phase 2 — Staffing normalisation + payroll groups + multi-assignment
-- + matrix manager (P2-19, P2-20, P2-21, P2-22)
--
-- Five new master / child tables and two FK columns on existing entities.
-- All five new tables are independent from existing flows — populating them
-- is opt-in. The existing free-text columns on staffing.position
-- (grade VARCHAR, job_family VARCHAR, job_level VARCHAR, employment_type
-- VARCHAR) stay; new *_id FK columns are added alongside so any tenant can
-- adopt the normalised model gradually without breaking the legacy code path.

-- ── staffing.grade ──────────────────────────────────────────────────────────
-- Reference catalogue of salary bands. Replaces free-text grade strings.

CREATE TABLE IF NOT EXISTS staffing.grade (
    id              UUID         PRIMARY KEY,
    code            VARCHAR(40)  NOT NULL UNIQUE,
    name            VARCHAR(160) NOT NULL,
    description     TEXT,
    level           INTEGER,
    min_salary      NUMERIC(14,2),
    max_salary      NUMERIC(14,2),
    currency        VARCHAR(3)   NOT NULL DEFAULT 'AZN',
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(80),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by      VARCHAR(80),
    CONSTRAINT chk_grade_salary CHECK (
        min_salary IS NULL OR max_salary IS NULL OR min_salary <= max_salary
    ),
    CONSTRAINT chk_grade_currency CHECK (
        length(currency) = 3 AND currency = upper(currency)
    )
);

CREATE INDEX IF NOT EXISTS idx_grade_active ON staffing.grade (active);


-- ── staffing.job_family ─────────────────────────────────────────────────────
-- Broad classification: Engineering / Operations / Sales / Finance / HR / etc.

CREATE TABLE IF NOT EXISTS staffing.job_family (
    id              UUID         PRIMARY KEY,
    code            VARCHAR(40)  NOT NULL UNIQUE,
    name            VARCHAR(160) NOT NULL,
    description     TEXT,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(80),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by      VARCHAR(80)
);

CREATE INDEX IF NOT EXISTS idx_job_family_active ON staffing.job_family (active);


-- ── staffing.job_function ───────────────────────────────────────────────────
-- More specific role within a family. Engineering family → Backend Engineer,
-- Frontend Engineer, SRE, DevOps job functions, etc.

CREATE TABLE IF NOT EXISTS staffing.job_function (
    id              UUID         PRIMARY KEY,
    code            VARCHAR(40)  NOT NULL UNIQUE,
    name            VARCHAR(160) NOT NULL,
    description     TEXT,
    job_family_id   UUID         REFERENCES staffing.job_family (id) ON DELETE SET NULL,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(80),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by      VARCHAR(80)
);

CREATE INDEX IF NOT EXISTS idx_job_function_family ON staffing.job_function (job_family_id, active);


-- ── payroll.payroll_group ───────────────────────────────────────────────────
-- Groups of employees that share a pay cycle, bank file format, and rule
-- overrides. Lets the engine route multi-entity orgs through different
-- payment calendars without conditional logic in PayrollEngine.

CREATE TABLE IF NOT EXISTS payroll.payroll_group (
    id                   UUID         PRIMARY KEY,
    code                 VARCHAR(40)  NOT NULL UNIQUE,
    name                 VARCHAR(160) NOT NULL,
    description          TEXT,
    pay_cycle            VARCHAR(20)  NOT NULL DEFAULT 'MONTHLY',
    bank_file_format     VARCHAR(20)  NOT NULL DEFAULT 'CSV',
    default_currency     VARCHAR(3)   NOT NULL DEFAULT 'AZN',
    rules_json           JSONB,
    active               BOOLEAN      NOT NULL DEFAULT TRUE,
    is_default           BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by           VARCHAR(80),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by           VARCHAR(80),
    CONSTRAINT chk_pg_pay_cycle CHECK (pay_cycle IN ('MONTHLY','BI_WEEKLY','WEEKLY','SEMI_MONTHLY')),
    CONSTRAINT chk_pg_file_format CHECK (bank_file_format IN ('CSV','SWIFT_MT103','SEPA','CUSTOM')),
    CONSTRAINT chk_pg_currency CHECK (length(default_currency) = 3 AND default_currency = upper(default_currency))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_payroll_group_default
    ON payroll.payroll_group (is_default)
    WHERE is_default = TRUE;

-- Seed the default group so the override-or-fallback chain has a stable
-- root. Same pattern as M66 leave_group.
INSERT INTO payroll.payroll_group (id, code, name, description, pay_cycle, bank_file_format, default_currency, is_default, active, created_by)
VALUES (
    '00000000-0000-0000-0000-000000000002',
    'DEFAULT',
    'Default payroll group',
    'System-seeded fallback group — employees with payroll_group_id IS NULL fall through to this entry.',
    'MONTHLY', 'CSV', 'AZN', TRUE, TRUE, 'system'
) ON CONFLICT (code) DO NOTHING;


-- ── core_hr.employee_assignment ────────────────────────────────────────────
-- Effective-dated assignment of an employee to a position. Distinct from
-- the scalar position_id on employee — that's the "current primary"
-- shortcut for fast lookup; this table holds the full history including
-- secondments and matrix roles.
--
-- Implements the M62 EffectiveDatedRecord pattern so the close-prior-slice
-- helper works without new code.

CREATE TABLE IF NOT EXISTS core_hr.employee_assignment (
    id                   UUID         PRIMARY KEY,
    employee_id          UUID         NOT NULL REFERENCES core_hr.employee (id) ON DELETE CASCADE,
    position_id          UUID         NOT NULL,
    assignment_type      VARCHAR(20)  NOT NULL DEFAULT 'PRIMARY',
    -- 0..100 — how much of the employee's working time goes to this assignment.
    -- PRIMARY is typically 100, SECONDMENT is 50, MATRIX is 25, etc.
    allocation_percent   NUMERIC(5,2) NOT NULL DEFAULT 100.00,
    -- Optional matrix / dotted-line manager for this specific assignment.
    -- Distinct from employee.manager_id (the line manager on the primary).
    matrix_manager_id    UUID,
    effective_from       DATE         NOT NULL,
    effective_to         DATE,
    notes                TEXT,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by           VARCHAR(80),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by           VARCHAR(80),
    CONSTRAINT chk_ea_type CHECK (assignment_type IN (
        'PRIMARY','ACTING','SECONDMENT','MATRIX','PROJECT'
    )),
    CONSTRAINT chk_ea_allocation CHECK (allocation_percent > 0 AND allocation_percent <= 100),
    CONSTRAINT chk_ea_date_order CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE INDEX IF NOT EXISTS idx_ea_employee_effective
    ON core_hr.employee_assignment (employee_id, effective_from DESC);

CREATE INDEX IF NOT EXISTS idx_ea_position
    ON core_hr.employee_assignment (position_id);

-- One open PRIMARY assignment per employee at a time (effective_to IS NULL).
-- Multiple non-primary types (acting, secondment) can co-exist.
CREATE UNIQUE INDEX IF NOT EXISTS uq_ea_one_open_primary
    ON core_hr.employee_assignment (employee_id)
    WHERE effective_to IS NULL AND assignment_type = 'PRIMARY';


-- ── core_hr.employee — new FK columns ──────────────────────────────────────

ALTER TABLE core_hr.employee
    ADD COLUMN IF NOT EXISTS matrix_manager_id  UUID,
    ADD COLUMN IF NOT EXISTS payroll_group_id   UUID;

-- Both soft FKs (ON DELETE SET NULL).
ALTER TABLE core_hr.employee
    DROP CONSTRAINT IF EXISTS fk_employee_payroll_group;
ALTER TABLE core_hr.employee
    ADD CONSTRAINT fk_employee_payroll_group
    FOREIGN KEY (payroll_group_id) REFERENCES payroll.payroll_group (id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_employee_payroll_group ON core_hr.employee (payroll_group_id);
CREATE INDEX IF NOT EXISTS idx_employee_matrix_manager ON core_hr.employee (matrix_manager_id);


-- ── staffing.position — new FK columns ─────────────────────────────────────
-- Optional FKs alongside the existing free-text grade / job_family / etc.
-- When non-null, they take precedence over the strings for reports / search.

ALTER TABLE staffing.position
    ADD COLUMN IF NOT EXISTS grade_id          UUID REFERENCES staffing.grade (id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS job_family_id     UUID REFERENCES staffing.job_family (id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS job_function_id   UUID REFERENCES staffing.job_function (id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_position_grade ON staffing.position (grade_id);
CREATE INDEX IF NOT EXISTS idx_position_job_family ON staffing.position (job_family_id);
CREATE INDEX IF NOT EXISTS idx_position_job_function ON staffing.position (job_function_id);
