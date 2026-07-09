-- ----------------------------------------------------------------------------
-- M465 — GL journal approval and posting workflow
--
-- GL journals flow: DRAFT → APPROVED → POSTED
-- Only APPROVED journals may be posted. POSTED journals cannot be regenerated.
-- ----------------------------------------------------------------------------

ALTER TABLE payroll.gl_journal
  ADD COLUMN IF NOT EXISTS approved_by  VARCHAR(200),
  ADD COLUMN IF NOT EXISTS approved_at  TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS posted_by    VARCHAR(200),
  ADD COLUMN IF NOT EXISTS posted_at    TIMESTAMPTZ;

-- Extend status check constraint to include APPROVED
ALTER TABLE payroll.gl_journal
  DROP CONSTRAINT IF EXISTS gl_journal_status_ck;

ALTER TABLE payroll.gl_journal
  ADD CONSTRAINT gl_journal_status_ck CHECK (
    status IN ('DRAFT', 'APPROVED', 'POSTED')
  );
