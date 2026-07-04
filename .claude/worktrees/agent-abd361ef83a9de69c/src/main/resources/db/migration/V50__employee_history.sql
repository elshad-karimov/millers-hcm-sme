-- M62 / Phase 1 — Effective-dated history tables (P1-10 + P1-11)
--
-- Two history tables that finally let us answer "what was this employee's
-- {department / position / manager / status} on date D" with an indexed query
-- instead of parsing JSONB event logs.
--
--   * core_hr.employee_employment_history — snapshot of position / department /
--     manager / grade / cost centre / employment_type / fte_percent at any
--     point in time. Written by ContractChangeService.apply() and by
--     EmployeeService.create() (initial slice at hire).
--
--   * core_hr.employee_status_history — snapshot of employmentStatus.
--     Written by EmployeeService.changeStatus() and EmployeeService.create()
--     (initial slice at hire).
--
-- Both follow the (effective_from, effective_to) convention; at most one row
-- per employee has effective_to IS NULL at any time.
--
-- Backfill at the bottom inserts one initial slice per existing employee
-- with effective_from = hire_date — so first-time deployments have a
-- consistent history even though every change before today is unknown.

-- ── employee_employment_history ───────────────────────────────────────────

CREATE TABLE IF NOT EXISTS core_hr.employee_employment_history (
    id                  UUID         PRIMARY KEY,
    employee_id         UUID         NOT NULL REFERENCES core_hr.employee (id) ON DELETE CASCADE,
    effective_from      DATE         NOT NULL,
    effective_to        DATE,
    position_id         UUID,
    position_title      VARCHAR(160),
    org_unit_id         UUID,
    department_name     VARCHAR(160),
    cost_centre         VARCHAR(64),
    manager_id          UUID,
    employment_type     VARCHAR(20),
    fte_percent         NUMERIC(5,2),
    -- "what caused this slice?" — free-text label + optional FK-by-string to
    -- a transaction record (contract_change.id, recruitment offer.id, etc.)
    change_reason       TEXT,
    source_module       VARCHAR(40),   -- e.g. 'LIFECYCLE', 'CORE_HR', 'RECRUITMENT'
    source_entity       VARCHAR(60),   -- e.g. 'ContractChange', 'Employee'
    source_id           VARCHAR(64),   -- entity id as text (mixed schemas → no hard FK)
    changed_by          VARCHAR(80),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_emp_hist_date_order
        CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE INDEX IF NOT EXISTS idx_emp_hist_employee_effective
    ON core_hr.employee_employment_history (employee_id, effective_from DESC);

-- Fast "find the currently-open slice for this employee" query — every
-- new transition first looks up the row it has to close, and at most one
-- such row exists per employee. Partial unique index enforces that invariant.
CREATE UNIQUE INDEX IF NOT EXISTS uq_emp_hist_one_open_per_employee
    ON core_hr.employee_employment_history (employee_id)
    WHERE effective_to IS NULL;

-- ── employee_status_history ───────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS core_hr.employee_status_history (
    id                  UUID         PRIMARY KEY,
    employee_id         UUID         NOT NULL REFERENCES core_hr.employee (id) ON DELETE CASCADE,
    status              VARCHAR(20)  NOT NULL,
    effective_from      DATE         NOT NULL,
    effective_to        DATE,
    reason              TEXT,
    source_module       VARCHAR(40),
    source_entity       VARCHAR(60),
    source_id           VARCHAR(64),
    changed_by          VARCHAR(80),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_emp_status_hist_date_order
        CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT chk_emp_status_hist_value
        CHECK (status IN (
            'ACTIVE','ON_PROBATION','ON_LEAVE','ON_BUSINESS_TRIP',
            'SUSPENDED','TERMINATED','RETIRED','CONTRACTOR','INTERN'
        ))
);

CREATE INDEX IF NOT EXISTS idx_emp_status_hist_employee_effective
    ON core_hr.employee_status_history (employee_id, effective_from DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uq_emp_status_hist_one_open_per_employee
    ON core_hr.employee_status_history (employee_id)
    WHERE effective_to IS NULL;

-- ── Backfill: one initial slice per existing employee ─────────────────────
-- Idempotent — only inserts when no history row exists for that employee.

INSERT INTO core_hr.employee_employment_history (
    id, employee_id, effective_from, effective_to,
    position_id, position_title, org_unit_id, department_name, cost_centre,
    manager_id, employment_type, fte_percent,
    change_reason, source_module, source_entity, source_id, changed_by, created_at
)
SELECT
    gen_random_uuid(),
    e.id,
    e.hire_date,
    NULL,
    e.position_id, e.position_title, e.org_unit_id, e.department_name, e.cost_centre,
    e.manager_id, e.employment_type, e.fte_percent,
    'Initial snapshot (M62 backfill)', 'CORE_HR', 'Employee', e.id::text,
    COALESCE(e.created_by, 'system'),
    now()
FROM core_hr.employee e
WHERE NOT EXISTS (
    SELECT 1 FROM core_hr.employee_employment_history h WHERE h.employee_id = e.id
);

INSERT INTO core_hr.employee_status_history (
    id, employee_id, status, effective_from, effective_to,
    reason, source_module, source_entity, source_id, changed_by, created_at
)
SELECT
    gen_random_uuid(),
    e.id,
    e.employment_status::text,
    e.hire_date,
    NULL,
    'Initial snapshot (M62 backfill)', 'CORE_HR', 'Employee', e.id::text,
    COALESCE(e.created_by, 'system'),
    now()
FROM core_hr.employee e
WHERE NOT EXISTS (
    SELECT 1 FROM core_hr.employee_status_history h WHERE h.employee_id = e.id
);
