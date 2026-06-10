-- ----------------------------------------------------------------------------
-- M274 — Recruitment PRD §4: Requisition extensions.
--
-- Upgrades the Vacancy entity from a plain job ad into a proper
-- recruitment requisition:
--
--   requisition_type   — NEW_HEADCOUNT / REPLACEMENT / TEMPORARY /
--                        PROJECT / SEASONAL / INTERNSHIP / CONTRACTOR /
--                        MASS_HIRING / EXECUTIVE / INTERNAL
--   hiring_reason      — why this hire is happening (resignation,
--                        expansion, new project, …)
--   target_start_date  — when the hire should start
--   cost_centre        — finance attribution
--   employment_type    — FULL_TIME / PART_TIME / etc (mirrors the
--                        position field so the offer can default it)
--   replaced_employee_id — for REPLACEMENT requisitions, who left
--
-- Status vocabulary is also widened (DRAFT / PENDING_APPROVAL /
-- APPROVED / REJECTED / PUBLISHED / PAUSED added) — no DB constraint
-- exists on the column, so this is enum-side only. M275 wires the
-- approval state machine on top of the new states; until then the
-- service keeps defaulting to OPEN so existing flows are unaffected.
-- ----------------------------------------------------------------------------

ALTER TABLE recruitment.vacancy
  ADD COLUMN IF NOT EXISTS requisition_type     VARCHAR(32) NOT NULL DEFAULT 'NEW_HEADCOUNT',
  ADD COLUMN IF NOT EXISTS hiring_reason        VARCHAR(64),
  ADD COLUMN IF NOT EXISTS target_start_date    DATE,
  ADD COLUMN IF NOT EXISTS cost_centre          VARCHAR(64),
  ADD COLUMN IF NOT EXISTS employment_type      VARCHAR(32),
  ADD COLUMN IF NOT EXISTS replaced_employee_id UUID;

-- Replacement requisitions are the common drill-down ("who are we
-- backfilling?") — partial index keeps it cheap.
CREATE INDEX IF NOT EXISTS idx_vacancy_replaced_employee
    ON recruitment.vacancy(replaced_employee_id)
    WHERE replaced_employee_id IS NOT NULL;
