-- ----------------------------------------------------------------------------
-- M280 — Recruitment PRD §9 (candidate application) + §46 (consent light).
--
-- candidate.consent_at / consent_version — explicit privacy-consent
-- capture for candidates who apply through the public portal. The
-- version string identifies which consent text they accepted, so a
-- later policy update can re-prompt without losing the original
-- evidence.
--
-- application.posting_id — which channel/language posting drove the
-- application (PRD §44 source tracking). Nullable: HR-entered
-- applications have no posting.
-- ----------------------------------------------------------------------------

ALTER TABLE recruitment.candidate
  ADD COLUMN IF NOT EXISTS consent_at      TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS consent_version VARCHAR(20);

ALTER TABLE recruitment.application
  ADD COLUMN IF NOT EXISTS posting_id UUID;

CREATE INDEX IF NOT EXISTS idx_application_posting
    ON recruitment.application(posting_id)
    WHERE posting_id IS NOT NULL;
