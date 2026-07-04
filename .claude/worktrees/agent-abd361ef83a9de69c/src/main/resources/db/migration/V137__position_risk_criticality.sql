-- ----------------------------------------------------------------------------
-- M256 — Phase I: Position Risk & Criticality flags (PRD §31).
--
-- Adds the criticality / risk-management fields needed to identify
-- which positions are key-person risks and require succession planning.
-- Surfaced on the PositionFormPage as a "Risk & Criticality" Collapse
-- panel and as a 🔴 badge on the Positions list. Also unlocks the
-- M103 succession bench-depth report to highlight critical roles
-- without named successors.
--
--   critical_flag           — is this position critical to business continuity?
--   business_impact_score   — 1 (low) … 5 (extreme) — quantified impact if vacant
--   risk_category           — KEY_PERSON / REGULATORY / OPERATIONAL / SPECIALIST / EXECUTIVE
--   key_skill_concentration — true when the role depends on hard-to-replace skills
--   successor_required      — must have a named successor at all times
--
-- All defaults are "no risk" so existing positions don't suddenly
-- become flagged. The partial index on critical_flag = TRUE keeps
-- the index size tiny since most positions are not critical.
-- ----------------------------------------------------------------------------

ALTER TABLE staffing.position
  ADD COLUMN IF NOT EXISTS critical_flag           BOOLEAN     NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS business_impact_score   SMALLINT,
  ADD COLUMN IF NOT EXISTS risk_category           VARCHAR(32),
  ADD COLUMN IF NOT EXISTS key_skill_concentration BOOLEAN     NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS successor_required      BOOLEAN     NOT NULL DEFAULT FALSE;

-- Range check on business_impact_score — 1 (low) through 5 (extreme),
-- or NULL when not scored.
ALTER TABLE staffing.position
  ADD CONSTRAINT chk_position_business_impact_score
  CHECK (business_impact_score IS NULL
         OR (business_impact_score BETWEEN 1 AND 5));

-- Partial index covering only critical positions — cheap because most
-- rows will have critical_flag = FALSE. Used by the M103 succession
-- bench-depth report.
CREATE INDEX IF NOT EXISTS idx_position_critical
    ON staffing.position(critical_flag)
    WHERE critical_flag = TRUE;
