-- M66 / Phase 1 — Leave groups + per-group entitlement overrides (P1-08)
--
-- Until now every employee shared a single set of LeaveType entitlements
-- (e.g. all employees accrue 21 days annual leave). Real-world HR needs:
--   * Staff           — 21 days annual
--   * Management      — 28 days annual
--   * Union members   — per collective bargaining agreement
--
-- Design: keep LeaveType as the global catalogue (annual / sick / etc.)
-- AND add a thin override layer keyed by (leave_group, leave_type). When
-- LeaveAccrualService computes the monthly bump for an employee, it now:
--   1. Resolves the employee's leave_group_id (NULL → Default group)
--   2. Looks up the (group, type) entitlement override if any
--   3. Falls back to the LeaveType's own defaults
--
-- Zero changes for orgs that don't need group differentiation — the Default
-- group is seeded and the existing `employee.leave_group_id IS NULL` rows
-- behave identically to before.

-- ── leave_group ───────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS leave_mgmt.leave_group (
    id          UUID         PRIMARY KEY,
    code        VARCHAR(40)  NOT NULL UNIQUE,
    name        VARCHAR(160) NOT NULL,
    description TEXT,
    is_default  BOOLEAN      NOT NULL DEFAULT FALSE,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  VARCHAR(80),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by  VARCHAR(80)
);

-- At most one default group at a time — keeps the "employee without an
-- explicit group_id" resolution unambiguous.
CREATE UNIQUE INDEX IF NOT EXISTS uq_leave_group_default
    ON leave_mgmt.leave_group (is_default)
    WHERE is_default = TRUE;

-- Seed the default group so the override-or-fallback chain has a stable
-- root entry. New tenants typically rename this to "Standard" or similar
-- via the API.
INSERT INTO leave_mgmt.leave_group (id, code, name, description, is_default, active, created_by)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'DEFAULT',
    'Default leave policy',
    'System-seeded fallback group — every employee whose leave_group_id is NULL falls through to this entry.',
    TRUE, TRUE, 'system'
) ON CONFLICT (code) DO NOTHING;


-- ── employee.leave_group_id ───────────────────────────────────────────────

ALTER TABLE core_hr.employee
    ADD COLUMN IF NOT EXISTS leave_group_id UUID;

-- Soft FK: same schema, but ON DELETE SET NULL so removing a group doesn't
-- cascade-orphan employees. The service layer prevents deleting a group
-- that has employees attached anyway.
ALTER TABLE core_hr.employee
    DROP CONSTRAINT IF EXISTS fk_employee_leave_group;
ALTER TABLE core_hr.employee
    ADD CONSTRAINT fk_employee_leave_group
    FOREIGN KEY (leave_group_id)
    REFERENCES leave_mgmt.leave_group (id)
    ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_employee_leave_group
    ON core_hr.employee (leave_group_id);


-- ── leave_group_entitlement ───────────────────────────────────────────────
--
-- Override table — only populated for (group, type) pairs that DIFFER from
-- the LeaveType's defaults. Empty = use LeaveType defaults.

CREATE TABLE IF NOT EXISTS leave_mgmt.leave_group_entitlement (
    id                              UUID         PRIMARY KEY,
    leave_group_id                  UUID         NOT NULL REFERENCES leave_mgmt.leave_group (id) ON DELETE CASCADE,
    leave_type_id                   UUID         NOT NULL REFERENCES leave_mgmt.leave_type (id) ON DELETE CASCADE,
    -- All three columns are nullable — NULL means "no override on this
    -- attribute, fall through to LeaveType.<same field>". Lets a group
    -- override only the annual-days budget while inheriting the type's
    -- seniority schedule, for instance.
    annual_entitlement_days         NUMERIC(6,2),
    monthly_accrual_days            NUMERIC(6,2),
    seniority_brackets_json         JSONB,
    notes                           TEXT,
    created_at                      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by                      VARCHAR(80),
    updated_at                      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by                      VARCHAR(80),
    CONSTRAINT uq_lge_group_type UNIQUE (leave_group_id, leave_type_id)
);

CREATE INDEX IF NOT EXISTS idx_lge_group
    ON leave_mgmt.leave_group_entitlement (leave_group_id);
