-- M323: resignation withdrawal reason + timestamp (Offboarding PRD §5)
ALTER TABLE lifecycle.resignation_request
    ADD COLUMN IF NOT EXISTS withdrawal_reason TEXT,
    ADD COLUMN IF NOT EXISTS withdrawn_at       TIMESTAMPTZ;
