-- ----------------------------------------------------------------------------
-- M290 — Recruitment PRD §20: interview calendar invites.
--
-- Adds the scheduling detail an RFC 5545 (.ics) invite needs — duration
-- and a free-text location (room name or video-meeting URL) — plus the
-- bookkeeping for re-sends: calendar_sequence (the iCalendar SEQUENCE,
-- bumped on every reschedule / cancel so clients accept the update) and
-- invite_sent_at (last time an invite went out, for the UI badge).
--
-- The invite UID is the interview id (stable across updates); the ICS is
-- emailed as an attachment to interviewer + candidate via the existing
-- M89 EmailService. Live Google/Outlook/Teams/Zoom API integration is a
-- documented later seam (needs per-tenant OAuth credentials).
-- ----------------------------------------------------------------------------

ALTER TABLE recruitment.interview
    ADD COLUMN IF NOT EXISTS duration_minutes  INT NOT NULL DEFAULT 60,
    ADD COLUMN IF NOT EXISTS location          VARCHAR(500),
    ADD COLUMN IF NOT EXISTS calendar_sequence INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS invite_sent_at    TIMESTAMPTZ;
