-- M81 — Organizational Structure expansion.
--
-- Three additions on top of the existing V3 org_unit + structure_version
-- tables:
--
--   1. Richer attribute columns on org_unit — cost-centre code, physical
--      location, contact email, GL account, headcount budget. All optional
--      so existing rows stay valid.
--
--   2. org_unit_policy — per-unit defaults for leave_group_id /
--      payroll_group_id. Resolved up the tree: an employee with a NULL
--      group_id and no override on their own unit walks the parent chain
--      until it finds a non-null policy, falling back to the seeded
--      DEFAULT group at the root.
--
--   3. org_unit_history — append-only slice per unit change. Distinct
--      from audit.audit_log because the latter records JSON-blob diffs;
--      this table is queried by the org page itself to render a unit's
--      lifecycle without scanning the global audit partition.

-- ── attribute columns on org_unit ──────────────────────────────────────────

ALTER TABLE organization.org_unit
    ADD COLUMN IF NOT EXISTS cost_centre_code   VARCHAR(64),
    ADD COLUMN IF NOT EXISTS location           VARCHAR(200),
    ADD COLUMN IF NOT EXISTS contact_email      VARCHAR(160),
    ADD COLUMN IF NOT EXISTS gl_account         VARCHAR(64),
    ADD COLUMN IF NOT EXISTS headcount_budget   INTEGER,
    ADD COLUMN IF NOT EXISTS active             BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE organization.org_unit
    DROP CONSTRAINT IF EXISTS chk_org_unit_headcount_nonneg;
ALTER TABLE organization.org_unit
    ADD CONSTRAINT chk_org_unit_headcount_nonneg
    CHECK (headcount_budget IS NULL OR headcount_budget >= 0);

CREATE INDEX IF NOT EXISTS idx_org_unit_cost_centre
    ON organization.org_unit (cost_centre_code)
    WHERE cost_centre_code IS NOT NULL;


-- ── org_unit_policy ────────────────────────────────────────────────────────
-- Soft FKs to leave.leave_group and payroll.payroll_group so a future
-- schema migration on either side doesn't cascade-delete policy rows. The
-- resolver walks parent_id when a column is NULL.

CREATE TABLE IF NOT EXISTS organization.org_unit_policy (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    org_unit_id         UUID         NOT NULL REFERENCES organization.org_unit (id) ON DELETE CASCADE,
    -- Tracks the policy alongside the org structure version so a draft
    -- can iterate without disturbing the ACTIVE version's policy.
    version_id          UUID         NOT NULL REFERENCES organization.structure_version (id) ON DELETE CASCADE,
    leave_group_id      UUID,
    payroll_group_id    UUID,
    notes               TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by          VARCHAR(80),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by          VARCHAR(80),
    -- One policy row per (unit, version) pair. Reorganising creates a
    -- new draft version and the policy is copied / overridden there.
    UNIQUE (org_unit_id, version_id)
);

CREATE INDEX IF NOT EXISTS idx_org_unit_policy_unit ON organization.org_unit_policy (org_unit_id);
CREATE INDEX IF NOT EXISTS idx_org_unit_policy_version ON organization.org_unit_policy (version_id);


-- ── org_unit_history ───────────────────────────────────────────────────────
-- Append-only — one row per change. The {kind, before, after} triple is
-- stored as JSONB so the same table covers create / update / remove
-- without schema gymnastics.

CREATE TABLE IF NOT EXISTS organization.org_unit_history (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    org_unit_id     UUID         NOT NULL,
    version_id      UUID         NOT NULL,
    change_kind     VARCHAR(20)  NOT NULL,
    before_value    JSONB,
    after_value     JSONB,
    change_reason   TEXT,
    changed_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    changed_by      VARCHAR(80),
    CONSTRAINT chk_org_history_kind CHECK (change_kind IN (
        'CREATE','UPDATE','REMOVE','MOVE','RENAME','HEAD_CHANGE','POLICY_CHANGE'
    ))
);

CREATE INDEX IF NOT EXISTS idx_org_history_unit
    ON organization.org_unit_history (org_unit_id, changed_at DESC);

CREATE INDEX IF NOT EXISTS idx_org_history_version
    ON organization.org_unit_history (version_id);
