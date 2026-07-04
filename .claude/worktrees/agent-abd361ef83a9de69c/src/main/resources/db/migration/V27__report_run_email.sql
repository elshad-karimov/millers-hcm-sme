-- Email delivery tracking on report_run (PRD 10.5).
--
-- Adds 4 columns so we can see whether a run's generated file was emailed,
-- to whom, and when. SKIPPED is set when the run had no recipients (e.g.
-- ad-hoc /run-now from the UI). NOT_REQUESTED is the default for legacy
-- rows + any run that intentionally bypasses email (sync /export downloads).
--
-- email_recipients is a comma-separated string snapshotted from the
-- schedule at delivery time so editing the schedule later doesn't
-- rewrite history.

ALTER TABLE reporting.report_run
    ADD COLUMN email_status     VARCHAR(16) NOT NULL DEFAULT 'NOT_REQUESTED',
    ADD COLUMN email_recipients TEXT,
    ADD COLUMN email_sent_at    TIMESTAMPTZ,
    ADD COLUMN email_error      TEXT;

ALTER TABLE reporting.report_run
    ADD CONSTRAINT chk_run_email_status
    CHECK (email_status IN ('NOT_REQUESTED', 'SKIPPED', 'SENT', 'FAILED'));

CREATE INDEX idx_run_email_status ON reporting.report_run (email_status)
    WHERE email_status IN ('FAILED', 'SENT');
