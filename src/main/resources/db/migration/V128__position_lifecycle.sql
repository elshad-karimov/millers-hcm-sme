-- ----------------------------------------------------------------------------
-- M243 — Position lifecycle (Phase A of Position Management spec)
--
-- Replaces the binary ACTIVE/CLOSED status with an 8-state lifecycle:
--   DRAFT → PENDING_APPROVAL → APPROVED → ACTIVE → (FROZEN | UNDER_REVIEW) →
--   ACTIVE → CLOSED → ARCHIVED
--
-- Existing data is preserved: rows currently in ACTIVE stay ACTIVE,
-- rows in CLOSED stay CLOSED. The status column is a plain VARCHAR(32)
-- with no CHECK constraint, so we don't need to touch the schema for
-- the enum widening — just for the new metadata columns.
-- ----------------------------------------------------------------------------

-- 1) Freeze + closure + approval metadata so each transition leaves a
--    breadcrumb directly on the row (handy for fast "show me the freeze
--    reason" lookups without joining the event journal).
ALTER TABLE staffing.position
  ADD COLUMN IF NOT EXISTS freeze_reason             TEXT,
  ADD COLUMN IF NOT EXISTS frozen_at                 TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS frozen_by                 VARCHAR(120),
  ADD COLUMN IF NOT EXISTS scheduled_unfreeze_date   DATE,
  ADD COLUMN IF NOT EXISTS closure_reason            TEXT,
  ADD COLUMN IF NOT EXISTS closed_at                 TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS closed_by                 VARCHAR(120),
  ADD COLUMN IF NOT EXISTS approved_at               TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS approved_by               VARCHAR(120),
  ADD COLUMN IF NOT EXISTS review_reason             TEXT,
  ADD COLUMN IF NOT EXISTS under_review_at           TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS under_review_by           VARCHAR(120);

-- 2) Lifecycle event journal — the full audit history of every state
--    transition. One row per transition, in chronological order. The
--    breadcrumb columns above are a denormalised cache of the most
--    recent freeze/closure/approval; this table is the source of truth.
CREATE TABLE IF NOT EXISTS staffing.position_lifecycle_event (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    position_id   UUID NOT NULL REFERENCES staffing.position(id) ON DELETE CASCADE,
    -- Nullable for the very first event (creation row has no "from".)
    from_status   VARCHAR(32),
    to_status     VARCHAR(32) NOT NULL,
    -- Free-text reason — surfaced in the UI and in audit reports.
    reason        TEXT,
    -- Optional extra comments / handover notes.
    comments      TEXT,
    -- Set when freezing with a scheduled auto-unfreeze.
    scheduled_unfreeze_date DATE,
    actor         VARCHAR(120),
    occurred_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_position_lifecycle_position_time
    ON staffing.position_lifecycle_event(position_id, occurred_at DESC);

-- 3) Backfill a synthetic creation event for every existing position so
--    the timeline isn't empty on day one. Status of ACTIVE rows stays
--    ACTIVE (no change), CLOSED rows stay CLOSED.
INSERT INTO staffing.position_lifecycle_event
    (position_id, from_status, to_status, reason, actor, occurred_at)
SELECT p.id, NULL, p.status, 'Backfill — pre-M243 history not preserved',
       COALESCE(p.created_by, 'system'), p.created_at
FROM staffing.position p
WHERE NOT EXISTS (
    SELECT 1 FROM staffing.position_lifecycle_event e WHERE e.position_id = p.id
);
