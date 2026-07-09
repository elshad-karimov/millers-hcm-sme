-- ----------------------------------------------------------------------------
-- M466 — GL journal reversal
--
-- Only POSTED journals may be reversed. A reversed journal creates a new
-- journal with inverted debit/credit lines, links to the original, and marks
-- the original as REVERSED. A journal may only be reversed once.
-- ----------------------------------------------------------------------------

ALTER TABLE payroll.gl_journal
  ADD COLUMN IF NOT EXISTS reversed_journal_id UUID,
  ADD CONSTRAINT fk_gl_journal_reversed FOREIGN KEY (reversed_journal_id)
    REFERENCES payroll.gl_journal(id);

-- Add REVERSED status
ALTER TABLE payroll.gl_journal
  DROP CONSTRAINT IF EXISTS gl_journal_status_ck;

ALTER TABLE payroll.gl_journal
  ADD CONSTRAINT gl_journal_status_ck CHECK (
    status IN ('DRAFT', 'APPROVED', 'POSTED', 'REVERSED')
  );

CREATE INDEX IF NOT EXISTS idx_gl_journal_reversed ON payroll.gl_journal(reversed_journal_id)
  WHERE reversed_journal_id IS NOT NULL;
