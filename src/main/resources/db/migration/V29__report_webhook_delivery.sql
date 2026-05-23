-- Slack / Teams webhook delivery for scheduled reports (PRD 10.5).
--
-- Mirrors the M20 email-delivery column shape so the two transports
-- are observable and resendable through the same patterns.
--
-- A schedule records ONE webhook target (URL + type). Per-run state
-- (`report_run.webhook_*`) snapshots the URL at delivery time so
-- editing the schedule later doesn't rewrite history.

ALTER TABLE reporting.report_schedule
    ADD COLUMN webhook_type VARCHAR(16) NOT NULL DEFAULT 'NONE',
    ADD COLUMN webhook_url  TEXT;

ALTER TABLE reporting.report_schedule
    ADD CONSTRAINT chk_schedule_webhook_type
    CHECK (webhook_type IN ('NONE', 'SLACK', 'TEAMS'));

ALTER TABLE reporting.report_run
    ADD COLUMN webhook_status   VARCHAR(16) NOT NULL DEFAULT 'NOT_REQUESTED',
    ADD COLUMN webhook_target   TEXT,
    ADD COLUMN webhook_sent_at  TIMESTAMPTZ,
    ADD COLUMN webhook_error    TEXT;

ALTER TABLE reporting.report_run
    ADD CONSTRAINT chk_run_webhook_status
    CHECK (webhook_status IN ('NOT_REQUESTED', 'SKIPPED', 'SENT', 'FAILED'));

CREATE INDEX idx_run_webhook_status ON reporting.report_run (webhook_status)
    WHERE webhook_status IN ('FAILED', 'SENT');
