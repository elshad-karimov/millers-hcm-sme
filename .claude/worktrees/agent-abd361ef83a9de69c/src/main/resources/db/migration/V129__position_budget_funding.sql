-- ----------------------------------------------------------------------------
-- M244 — Position budget + funding (Phase B of Position Management spec).
--
-- Two new tables behind the Position entity:
--
--   staffing.position_budget   — versioned monthly-cost components.
--                                One row per (position, effective_from). The
--                                row whose effective_from is the latest <=
--                                today is the "active" budget. Older rows
--                                stay for variance / historical reporting.
--
--   staffing.position_funding  — singleton per position. Funding status
--                                (FUNDED / UNFUNDED / …), source, owner,
--                                expiry. Mutable, not versioned — when it
--                                changes, the row is overwritten and the
--                                audit log captures the diff.
--
-- Backfill: every existing position gets an UNFUNDED row so the gate has
-- something to read on first call.
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS staffing.position_budget (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    position_id     UUID NOT NULL REFERENCES staffing.position(id) ON DELETE CASCADE,
    -- Versioning. The row whose effective_from <= today and (effective_to IS
    -- NULL OR effective_to >= today) is the active budget. Multiple budgets
    -- with no overlap are allowed; overlap is an app-level error.
    effective_from  DATE NOT NULL,
    effective_to    DATE,

    -- Component breakdown — all monthly amounts in `currency`.
    -- Total monthly cost is computed as the sum of these six columns; we
    -- don't store it as a generated column for portability across PG / H2.
    budgeted_basic_salary  NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (budgeted_basic_salary >= 0),
    budgeted_allowances    NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (budgeted_allowances >= 0),
    budgeted_employer_tax  NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (budgeted_employer_tax >= 0),
    budgeted_bonus         NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (budgeted_bonus >= 0),
    budgeted_overtime      NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (budgeted_overtime >= 0),
    budgeted_benefits      NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (budgeted_benefits >= 0),

    currency      VARCHAR(3)   NOT NULL DEFAULT 'AZN',
    budget_owner  VARCHAR(120),
    notes         TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(120),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(120)
);

CREATE INDEX IF NOT EXISTS idx_position_budget_position_eff
    ON staffing.position_budget(position_id, effective_from DESC);

CREATE TABLE IF NOT EXISTS staffing.position_funding (
    position_id    UUID PRIMARY KEY REFERENCES staffing.position(id) ON DELETE CASCADE,
    status         VARCHAR(32) NOT NULL DEFAULT 'UNFUNDED',
    funding_source VARCHAR(160),   -- e.g. "Department budget", "Grant GR-2026-01", "Project Alpha"
    funding_owner  VARCHAR(120),   -- person / role accountable for the funding
    funding_expiry DATE,           -- when grant / project funding lapses
    notes          TEXT,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by     VARCHAR(120)
);

-- Index on status for the "list unfunded" dashboard query.
CREATE INDEX IF NOT EXISTS idx_position_funding_status
    ON staffing.position_funding(status);

-- Backfill: every existing position gets an UNFUNDED funding row so the
-- gate code can safely assume the row exists. New positions created
-- after this migration will get the same default via the entity's
-- @PrePersist (set elsewhere).
INSERT INTO staffing.position_funding (position_id, status, updated_by)
SELECT p.id, 'UNFUNDED', 'system'
FROM staffing.position p
WHERE NOT EXISTS (
    SELECT 1 FROM staffing.position_funding f WHERE f.position_id = p.id
);
