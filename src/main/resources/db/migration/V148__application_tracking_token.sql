-- ----------------------------------------------------------------------------
-- M282 — Recruitment PRD §9: candidate application tracking.
--
-- Each public-portal application gets a 32-char secure random token;
-- the candidate tracks their status anonymously at
-- /careers/?track=<token>. The token IS the credential (same model as
-- the M122 pre-boarding magic link and M139 letter-verify token) —
-- no candidate account needed, which is the pragmatic v1 of the
-- PRD's candidate-portal account system.
--
-- Nullable: HR-entered and internal applications have no token.
-- ----------------------------------------------------------------------------

ALTER TABLE recruitment.application
  ADD COLUMN IF NOT EXISTS tracking_token VARCHAR(64) UNIQUE;
