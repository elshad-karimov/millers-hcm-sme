-- ----------------------------------------------------------------------------
-- M294 — Recruitment PRD §46/§47: candidate consent retention + anonymisation.
--
-- A candidate past its retention window (consent / last-contact / created
-- anchor + retention months) can be anonymised: PII scrubbed in place and
-- PII child rows removed, while the candidate + its applications stay for
-- funnel analytics. anonymized_at marks it done and excludes it from future
-- sweeps + the duplicate scan.
-- ----------------------------------------------------------------------------

ALTER TABLE recruitment.candidate
    ADD COLUMN IF NOT EXISTS anonymized_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_candidate_anonymized
    ON recruitment.candidate (anonymized_at)
    WHERE anonymized_at IS NULL;
