-- ----------------------------------------------------------------------------
-- M247 — Workforce planning + scenarios (Phase E).
--
-- Strategic planning layer above the operational position model:
--
--   staffing.workforce_plan      — header per (legal entity × version).
--                                  scenario_type lets you fork a baseline
--                                  into what-if alternatives (e.g.
--                                  "expand stores +5", "10% reduction").
--                                  parent_plan_id links the fork back.
--                                  Lifecycle DRAFT → PENDING_APPROVAL →
--                                  APPROVED → ACTIVE → ARCHIVED, with
--                                  REJECTED branch back to DRAFT.
--
--   staffing.workforce_plan_line — line per (org unit × job-family or
--                                  position template). Planned head-
--                                  count, planned FTE, planned monthly
--                                  cost, change_type (ADD / EXPAND /
--                                  REDUCE / REPLACE / HOLD).
--
-- Distinct from M245 staffing_table (the current approved establishment
-- for government compliance). Workforce_plan is forward-looking,
-- internal, scenario-able.
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS staffing.workforce_plan (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legal_entity_id UUID NOT NULL REFERENCES organization.legal_entity(id),
    version_code    VARCHAR(64)  NOT NULL,   -- e.g. "2026-baseline", "2026-stores+5"
    title           VARCHAR(200),
    -- BASELINE: the current planned trajectory.
    -- EXPANSION / REDUCTION / RESTRUCTURE / SEASONAL: scenarios off a baseline.
    -- WHAT_IF: free-form modelling.
    scenario_type   VARCHAR(32)  NOT NULL DEFAULT 'BASELINE',
    -- Planning horizon.
    effective_from  DATE NOT NULL,
    effective_to    DATE,
    status          VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    -- When this plan was cloned from another, link back.
    parent_plan_id  UUID REFERENCES staffing.workforce_plan(id),
    notes           TEXT,
    -- Approval breadcrumbs (same shape as staffing_table)
    submitted_by    VARCHAR(120),
    submitted_at    TIMESTAMPTZ,
    approved_by     VARCHAR(120),
    approved_at     TIMESTAMPTZ,
    rejected_by     VARCHAR(120),
    rejected_at     TIMESTAMPTZ,
    reject_reason   TEXT,
    archived_at     TIMESTAMPTZ,
    -- Audit
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(120),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(120)
);

CREATE INDEX IF NOT EXISTS idx_workforce_plan_legal_entity
    ON staffing.workforce_plan(legal_entity_id, effective_from DESC);
CREATE INDEX IF NOT EXISTS idx_workforce_plan_status
    ON staffing.workforce_plan(status);
CREATE INDEX IF NOT EXISTS idx_workforce_plan_parent
    ON staffing.workforce_plan(parent_plan_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_workforce_plan_version
    ON staffing.workforce_plan(legal_entity_id, version_code);

CREATE TABLE IF NOT EXISTS staffing.workforce_plan_line (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workforce_plan_id    UUID NOT NULL REFERENCES staffing.workforce_plan(id) ON DELETE CASCADE,
    line_no              INT  NOT NULL,
    -- Scope of the line. Either pinned to a specific org unit + position
    -- template, or scoped wider (org unit + job family).
    org_unit_id          UUID,
    org_unit_label       VARCHAR(200),
    job_family           VARCHAR(64),
    grade                VARCHAR(32),
    position_title       VARCHAR(200),
    -- Target metrics.
    planned_headcount    INT NOT NULL DEFAULT 0 CHECK (planned_headcount >= 0),
    planned_fte          NUMERIC(9,2) NOT NULL DEFAULT 0 CHECK (planned_fte >= 0),
    planned_monthly_cost NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (planned_monthly_cost >= 0),
    currency             VARCHAR(3) NOT NULL DEFAULT 'AZN',
    -- What kind of change vs the current establishment.
    -- ADD     — net new position(s)
    -- EXPAND  — increase headcount on an existing position
    -- REDUCE  — decrease headcount on an existing position
    -- REPLACE — same count, different shape (re-grade, re-title)
    -- HOLD    — no change
    change_type          VARCHAR(32),
    target_start_date    DATE,   -- when this change should land
    justification        TEXT,
    notes                TEXT
);

CREATE INDEX IF NOT EXISTS idx_workforce_plan_line_plan
    ON staffing.workforce_plan_line(workforce_plan_id, line_no);
CREATE INDEX IF NOT EXISTS idx_workforce_plan_line_org_unit
    ON staffing.workforce_plan_line(org_unit_id);
