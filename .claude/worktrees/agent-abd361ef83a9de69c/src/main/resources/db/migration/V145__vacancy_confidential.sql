-- ----------------------------------------------------------------------------
-- M277 — Recruitment PRD §41: Confidential recruitment.
--
-- confidential = TRUE restricts a requisition to its hiring team:
-- the named recruiter, the named hiring manager, and HR_ADMIN /
-- SYSTEM_ADMIN. Everyone else doesn't see it in lists and gets 404
-- on direct access (404 — not 403 — so the existence of the
-- requisition itself stays hidden).
--
-- Use cases (PRD §41): executive hiring, replacing a current
-- employee who doesn't know yet, sensitive-department hiring,
-- restructuring-related hiring.
-- ----------------------------------------------------------------------------

ALTER TABLE recruitment.vacancy
  ADD COLUMN IF NOT EXISTS confidential BOOLEAN NOT NULL DEFAULT FALSE;

-- Lists filter on it constantly once any confidential requisition exists.
CREATE INDEX IF NOT EXISTS idx_vacancy_confidential
    ON recruitment.vacancy(confidential)
    WHERE confidential = TRUE;
